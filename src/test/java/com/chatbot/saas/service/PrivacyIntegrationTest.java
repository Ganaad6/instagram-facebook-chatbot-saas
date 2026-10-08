package com.chatbot.saas.service;

import com.chatbot.saas.entity.*;
import com.chatbot.saas.repository.*;
import com.chatbot.saas.util.MetaSignedRequestTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Meta's deauthorize / data-deletion callbacks, a shop erasing a customer, and the chat
 * retention limit - over HTTP against the real database.
 */
@SpringBootTest
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrivacyIntegrationTest {

    private static final String APP_SECRET = "test_app_secret";

    @Autowired private MockMvc mockMvc;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CustomerDataService customerDataService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private long businessId;
    private String apiKey;
    private String metaUserId;

    @BeforeEach
    void connectedShop() throws Exception {
        String unique = String.valueOf(System.nanoTime());
        metaUserId = "fb-user-" + unique;
        String response = mockMvc.perform(post("/api/businesses/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Shop", "email", unique + "@example.com"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode registered = objectMapper.readTree(response);
        businessId = registered.get("id").asLong();
        apiKey = registered.get("apiKey").asText();

        Business business = businessRepository.findById(businessId).orElseThrow();
        business.setFacebookPageId("page-" + unique);
        business.setInstagramAccountId("ig-" + unique);
        business.setAccessToken("encrypted-page-token");
        business.setMetaUserId(metaUserId);
        businessRepository.save(business);
    }

    private String signedRequest(String userId) {
        return MetaSignedRequestTest.sign(
                "{\"algorithm\":\"HMAC-SHA256\",\"issued_at\":1700000000,\"user_id\":\"" + userId + "\"}", APP_SECRET);
    }

    private void assertDisconnected() {
        Business business = businessRepository.findById(businessId).orElseThrow();
        assertNull(business.getAccessToken());
        assertNull(business.getFacebookPageId());
        assertNull(business.getInstagramAccountId());
        assertNull(business.getMetaUserId());
    }

    @Test
    void deauthorizeDisconnectsTheShopsPage() throws Exception {
        mockMvc.perform(post("/webhook/meta/deauthorize")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("signed_request", signedRequest(metaUserId)))
                .andExpect(status().isOk());

        assertDisconnected();
    }

    @Test
    void dataDeletionDisconnectsAndReturnsACheckableConfirmation() throws Exception {
        String response = mockMvc.perform(post("/webhook/meta/data-deletion")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("signed_request", signedRequest(metaUserId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        String code = body.get("confirmation_code").asText();
        assertTrue(body.get("url").asText().endsWith("/data-deletion?code=" + code));
        assertDisconnected();

        mockMvc.perform(get("/api/public/data-deletion/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(get("/api/public/data-deletion/unknown-code"))
                .andExpect(status().isNotFound());
    }

    @Test
    void forgedOrMissingSignedRequestIsRejectedAndChangesNothing() throws Exception {
        mockMvc.perform(post("/webhook/meta/data-deletion")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("signed_request", MetaSignedRequestTest.sign(
                                "{\"algorithm\":\"HMAC-SHA256\",\"user_id\":\"" + metaUserId + "\"}", "wrong-secret")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/webhook/meta/deauthorize")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isBadRequest());

        assertNotNull(businessRepository.findById(businessId).orElseThrow().getAccessToken());
    }

    @Test
    void legalInfoIsPublic() throws Exception {
        mockMvc.perform(get("/api/public/legal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageRetentionDays").value(365));
    }

    @Test
    void erasingACustomerDeletesChatsAndAnonymizesOrders() throws Exception {
        Customer customer = customerWithHistory("psid-1", LocalDateTime.now());

        mockMvc.perform(delete("/api/businesses/" + businessId + "/customers/" + customer.getId()).header("X-API-Key", apiKey))
                .andExpect(status().isNoContent());

        Customer erased = customerRepository.findById(customer.getId()).orElseThrow();
        assertNotNull(erased.getErasedAt());
        assertNull(erased.getFacebookUserId());
        assertEquals(0, messageRepository.findFirstByCustomerIdOrderByIdDesc(customer.getId()).stream().count());
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from conversations where customer_id = ?", Integer.class, customer.getId()));
        Order order = orderRepository.findAll().stream()
                .filter(o -> o.getCustomer().getId().equals(customer.getId())).findFirst().orElseThrow();
        assertNull(order.getCustomerName());
        assertNull(order.getPhone());
        assertNull(order.getAddress());
        assertEquals(0, new BigDecimal("20000").compareTo(order.getTotalAmount()));

        // Gone from the dashboard, and a second erase is a 404
        mockMvc.perform(get("/api/businesses/" + businessId + "/chats/" + customer.getId()).header("X-API-Key", apiKey))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/businesses/" + businessId + "/customers/" + customer.getId()).header("X-API-Key", apiKey))
                .andExpect(status().isNotFound());
        // The same person writing again starts over as a new customer
        assertTrue(customerRepository.findByBusinessIdAndFacebookUserId(businessId, "psid-1").isEmpty());
    }

    @Test
    void anotherShopCannotEraseACustomer() throws Exception {
        Customer customer = customerWithHistory("psid-2", LocalDateTime.now());
        String other = mockMvc.perform(post("/api/businesses/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Other", "email", System.nanoTime() + "@example.com"))))
                .andReturn().getResponse().getContentAsString();
        String otherKey = objectMapper.readTree(other).get("apiKey").asText();

        mockMvc.perform(delete("/api/businesses/" + businessId + "/customers/" + customer.getId()).header("X-API-Key", otherKey))
                .andExpect(status().isForbidden());
        assertNull(customerRepository.findById(customer.getId()).orElseThrow().getErasedAt());
    }

    @Test
    void retentionDeletesOldMessagesOnly() {
        Customer customer = customerWithHistory("psid-3", LocalDateTime.now().minusDays(400));
        Business business = customer.getBusiness();
        messageRepository.save(Message.builder().business(business).customer(customer)
                .messageId("m-recent-" + System.nanoTime()).direction(Message.Direction.INBOUND)
                .senderType(Message.SenderType.CUSTOMER).content("recent").build());
        jdbcTemplate.update("update conversations set updated_at = now() - interval '400 days' where customer_id = ?",
                customer.getId());

        customerDataService.applyRetention();

        assertEquals("recent", messageRepository.findFirstByCustomerIdOrderByIdDesc(customer.getId()).orElseThrow().getContent());
        assertEquals(1, messageRepository.findAllByBusinessId(businessId).size());
        assertNull(jdbcTemplate.queryForObject(
                "select collected_phone from conversations where customer_id = ?", String.class, customer.getId()));
        // Orders keep their details
        assertEquals("99112233", orderRepository.findAll().stream()
                .filter(o -> o.getCustomer().getId().equals(customer.getId())).findFirst().orElseThrow().getPhone());
    }

    /** A customer with a conversation, one message sent at the given time, and an order. */
    private Customer customerWithHistory(String psid, LocalDateTime messageTime) {
        Business business = businessRepository.findById(businessId).orElseThrow();
        Category category = categoryRepository.save(Category.builder().business(business).name("Flowers").build());
        Product product = productRepository.save(Product.builder().business(business).category(category)
                .name("Rose").price(new BigDecimal("10000")).build());
        Customer customer = customerRepository.save(Customer.builder().business(business).facebookUserId(psid)
                .firstInteractionAt(messageTime).lastInteractionAt(messageTime).build());
        Conversation conversation = conversationRepository.save(Conversation.builder().business(business)
                .customer(customer).status(Conversation.Status.COMPLETED).platform("FACEBOOK")
                .collectedName("Bat").collectedPhone("99112233").collectedAddress("Ulaanbaatar").build());
        messageRepository.save(Message.builder().business(business).customer(customer).conversation(conversation)
                .messageId("m-" + System.nanoTime()).direction(Message.Direction.INBOUND)
                .senderType(Message.SenderType.CUSTOMER).content("old").sentAt(messageTime).createdAt(messageTime).build());
        orderRepository.save(Order.builder().business(business).customer(customer).product(product)
                .productName("Rose").unitPrice(new BigDecimal("10000")).quantity(2).totalAmount(new BigDecimal("20000"))
                .customerName("Bat").phone("99112233").address("Ulaanbaatar").platform(Order.Platform.FACEBOOK).build());
        return customer;
    }
}
