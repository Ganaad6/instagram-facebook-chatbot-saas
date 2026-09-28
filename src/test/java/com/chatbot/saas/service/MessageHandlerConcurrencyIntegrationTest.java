package com.chatbot.saas.service;

import com.chatbot.saas.entity.Business;
import com.chatbot.saas.entity.Conversation;
import com.chatbot.saas.entity.Customer;
import com.chatbot.saas.repository.BusinessRepository;
import com.chatbot.saas.repository.ConversationRepository;
import com.chatbot.saas.repository.CustomerRepository;
import com.chatbot.saas.repository.MessageRepository;
import com.chatbot.saas.service.MessageHandlerService.InboundMessage;
import com.chatbot.saas.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Meta can deliver several messages from one person at once (fast typing, button mashing) and
 * redelivers webhooks it thinks failed. Both must leave exactly one customer, one conversation
 * and one stored copy of each message.
 */
@SpringBootTest
@ActiveProfiles("test")
class MessageHandlerConcurrencyIntegrationTest {

    @Autowired private MessageHandlerService messageHandlerService;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private EncryptionUtil encryptionUtil;
    @Autowired private ThreadPoolTaskExecutor taskExecutor;

    @MockBean private MetaReplyService metaReplyService;

    private Business business;
    private String pageId;

    @BeforeEach
    void createConnectedBusiness() {
        pageId = "page-" + System.nanoTime();
        business = businessRepository.save(Business.builder()
                .name("Shop")
                .email(pageId + "@example.com")
                .facebookPageId(pageId)
                .accessToken(encryptionUtil.encrypt("page-token"))
                .build());
        // Simulate Meta's network latency: keeps each handler's transaction open long enough
        // that unsynchronized handlers would overlap and race
        doAnswer(inv -> {
            Thread.sleep(100);
            return null;
        }).when(metaReplyService).sendText(anyString(), anyString(), anyString());
    }

    @Test
    void redeliveredMessageIsProcessedOnce() throws Exception {
        InboundMessage message = new InboundMessage("FACEBOOK", "psid-dup", pageId, "mid-dup-" + pageId, "hi");
        for (int i = 0; i < 5; i++) {
            messageHandlerService.handleIncomingMessage(message);
        }
        awaitIdle();

        assertEquals(1, messageRepository.findAllByBusinessId(business.getId()).size());
        verify(metaReplyService, times(1)).sendText(anyString(), anyString(), anyString());
    }

    @Test
    void simultaneousMessagesFromNewCustomerShareOneCustomerAndConversation() throws Exception {
        int count = 8;
        for (int i = 0; i < count; i++) {
            messageHandlerService.handleIncomingMessage(
                    new InboundMessage("FACEBOOK", "psid-burst", pageId, "mid-" + i + "-" + pageId, "hi " + i));
        }
        awaitIdle();

        List<Customer> customers = customerRepository.findAllByBusinessId(business.getId());
        assertEquals(1, customers.size());
        assertEquals("psid-burst", customers.get(0).getFacebookUserId());
        assertNotNull(customers.get(0).getId());

        long activeConversations = conversationRepository.findAllByBusinessId(business.getId()).stream()
                .filter(c -> c.getStatus() == Conversation.Status.ACTIVE)
                .count();
        assertEquals(1, activeConversations);
        assertEquals(count, messageRepository.findAllByBusinessId(business.getId()).size());
    }

    private void awaitIdle() throws InterruptedException {
        ThreadPoolExecutor pool = taskExecutor.getThreadPoolExecutor();
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (pool.getActiveCount() == 0 && pool.getQueue().isEmpty()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Async message handling did not finish in time");
    }
}
