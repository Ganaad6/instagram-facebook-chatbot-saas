package com.chatbot.saas.service;

import com.chatbot.saas.entity.*;
import com.chatbot.saas.repository.*;
import com.chatbot.saas.service.MessageHandlerService.EchoMessage;
import com.chatbot.saas.service.MessageHandlerService.InboundMessage;
import com.chatbot.saas.util.EncryptionUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Human handoff end to end: customer messages and Meta echoes through the real handler and
 * database, and the staff API over HTTP. Only Meta itself is faked.
 */
@SpringBootTest
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HandoffIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MessageHandlerService messageHandlerService;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private EncryptionUtil encryptionUtil;
    @Autowired private ThreadPoolTaskExecutor taskExecutor;

    @MockBean private MetaReplyService metaReplyService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger mids = new AtomicInteger();
    private long businessId;
    private String apiKey;
    private String pageId;
    private String psid;

    @BeforeEach
    void connectedShopWithCatalog() throws Exception {
        pageId = "page-" + System.nanoTime();
        psid = "psid-" + System.nanoTime();
        String response = mockMvc.perform(post("/api/businesses/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Shop", "email", pageId + "@example.com"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode registered = objectMapper.readTree(response);
        businessId = registered.get("id").asLong();
        apiKey = registered.get("apiKey").asText();

        Business business = businessRepository.findById(businessId).orElseThrow();
        business.setFacebookPageId(pageId);
        business.setAccessToken(encryptionUtil.encrypt("page-token"));
        businessRepository.save(business);
        categoryRepository.save(Category.builder().business(business).name("Shoes").build());

        when(metaReplyService.sendText(anyString(), anyString(), anyString())).thenAnswer(inv -> "m_bot_" + mids.incrementAndGet());
        when(metaReplyService.sendMenuMessage(anyString(), anyString(), anyList(), anyString()))
                .thenAnswer(inv -> "m_bot_" + mids.incrementAndGet());
        when(metaReplyService.sendAgentText(anyString(), anyString(), anyBoolean(), anyString()))
                .thenAnswer(inv -> "m_agent_" + mids.incrementAndGet());
    }

    private void customerSays(String text) throws InterruptedException {
        messageHandlerService.handleIncomingMessage(
                new InboundMessage("FACEBOOK", psid, pageId, "m_in_" + mids.incrementAndGet(), text));
        awaitIdle();
    }

    private void echo(String mid, String appId, String text) throws InterruptedException {
        messageHandlerService.handleEcho(new EchoMessage("FACEBOOK", pageId, psid, mid, text, appId));
        awaitIdle();
    }

    private Customer customer() {
        return customerRepository.findByBusinessIdAndFacebookUserId(businessId, psid).orElseThrow();
    }

    private List<Message> messages() {
        return messageRepository.findAllByBusinessId(businessId);
    }

    @Test
    void askingForAPersonPausesTheBotUntilTheCustomerAsksForTheMenu() throws Exception {
        customerSays("сайн уу");
        verify(metaReplyService, times(1)).sendMenuMessage(eq(psid), anyString(), anyList(), anyString());

        customerSays("оператор");
        assertTrue(customer().isBotPaused(LocalDateTime.now()));
        assertNotNull(customer().getHandoffRequestedAt());
        verify(metaReplyService).sendText(psid, HandoffService.HANDOFF_ACK, "page-token");

        customerSays("захиалга хэзээ ирэх вэ?");
        // Recorded for staff, but the bot said nothing more
        assertTrue(messages().stream().anyMatch(m -> m.getContent().equals("захиалга хэзээ ирэх вэ?")));
        verify(metaReplyService, times(1)).sendMenuMessage(eq(psid), anyString(), anyList(), anyString());

        // Staff see the customer in the inbox
        String inbox = mockMvc.perform(get("/api/businesses/" + businessId + "/inbox").header("X-API-Key", apiKey))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(customer().getId(), objectMapper.readTree(inbox).get(0).get("id").asLong());

        customerSays("цэс");
        assertFalse(customer().isBotPaused(LocalDateTime.now()));
        verify(metaReplyService, times(2)).sendMenuMessage(eq(psid), anyString(), anyList(), anyString());
    }

    @Test
    void staffReplyFromMetaInboxPausesTheBotButOwnEchoesDoNot() throws Exception {
        customerSays("сайн уу");
        String botMid = messages().stream().filter(m -> m.getSenderType() == Message.SenderType.BOT)
                .findFirst().orElseThrow().getMessageId();

        echo(botMid, null, "menu");                  // our own reply, recognized by its mid
        echo("m_unrecorded", "test_app_id", "menu"); // our own reply, recognized by app id
        assertFalse(customer().isBotPaused(LocalDateTime.now()));

        echo("m_inbox_1", "263902037430900", "Сайн байна уу, би Сараа байна");
        assertTrue(customer().isBotPaused(LocalDateTime.now()));
        Message agent = messages().stream().filter(m -> m.getSenderType() == Message.SenderType.AGENT)
                .findFirst().orElseThrow();
        assertEquals("Сайн байна уу, би Сараа байна", agent.getContent());
        assertEquals(Message.Direction.OUTBOUND, agent.getDirection());

        customerSays("баярлалаа");
        verify(metaReplyService, times(1)).sendMenuMessage(eq(psid), anyString(), anyList(), anyString());
    }

    @Test
    void staffApiSendsReplyPausesBotAndResumeStartsFresh() throws Exception {
        customerSays("сайн уу");
        long customerId = customer().getId();
        String base = "/api/businesses/" + businessId + "/customers/" + customerId;

        mockMvc.perform(post(base + "/messages").header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Танд юугаар туслах вэ?\"}"))
                .andExpect(status().isCreated());
        verify(metaReplyService).sendAgentText(psid, "Танд юугаар туслах вэ?", false, "page-token");
        assertTrue(customer().isBotPaused(LocalDateTime.now()));

        String history = mockMvc.perform(get(base + "/messages").header("X-API-Key", apiKey))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode last = objectMapper.readTree(history).get(objectMapper.readTree(history).size() - 1);
        assertEquals("AGENT", last.get("senderType").asText());

        long conversationsBefore = conversationRepository.findAllByBusinessId(businessId).size();
        mockMvc.perform(post(base + "/resume-bot").header("X-API-Key", apiKey)).andExpect(status().isOk());
        customerSays("сайн уу");
        List<Conversation> conversations = conversationRepository.findAllByBusinessId(businessId);
        assertEquals(conversationsBefore + 1, conversations.size(), "resume starts a fresh bot conversation");
        assertEquals(1, conversations.stream().filter(c -> c.getStatus() == Conversation.Status.ACTIVE).count());
    }

    @Test
    void expiredPauseHandsBackToBot() throws Exception {
        customerSays("оператор");
        Customer customer = customer();
        customer.setBotPausedUntil(LocalDateTime.now().minusMinutes(1));
        customerRepository.save(customer);

        customerSays("сайн уу");

        assertNull(customer().getBotPausedUntil());
        assertNull(customer().getHandoffRequestedAt());
        verify(metaReplyService, atLeastOnce()).sendMenuMessage(eq(psid), anyString(), anyList(), anyString());
    }

    @Test
    void staffApiIsTenantScoped() throws Exception {
        customerSays("сайн уу");
        long customerId = customer().getId();
        String otherKey = objectMapper.readTree(mockMvc.perform(post("/api/businesses/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Other", "email", "o" + pageId + "@example.com"))))
                .andReturn().getResponse().getContentAsString()).get("apiKey").asText();
        long otherId = businessRepository.findByEmail("o" + pageId + "@example.com").orElseThrow().getId();

        // Another shop's key can't use this shop's path...
        mockMvc.perform(post("/api/businesses/" + businessId + "/customers/" + customerId + "/messages")
                        .header("X-API-Key", otherKey).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"hi\"}"))
                .andExpect(status().isForbidden());
        // ...nor reach this shop's customer through its own path
        mockMvc.perform(post("/api/businesses/" + otherId + "/customers/" + customerId + "/messages")
                        .header("X-API-Key", otherKey).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"hi\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/businesses/" + otherId + "/customers/" + customerId + "/messages")
                        .header("X-API-Key", otherKey))
                .andExpect(status().isNotFound());
        verify(metaReplyService, never()).sendAgentText(anyString(), anyString(), anyBoolean(), anyString());
    }

    private void awaitIdle() throws InterruptedException {
        ThreadPoolExecutor pool = taskExecutor.getThreadPoolExecutor();
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            // Every submitted task finished - active/queue counts alone briefly read 0 while a
            // task moves from the queue to a worker
            if (pool.getCompletedTaskCount() == pool.getTaskCount()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Async message handling did not finish in time");
    }
}
