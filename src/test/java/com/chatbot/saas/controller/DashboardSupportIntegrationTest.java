package com.chatbot.saas.controller;

import com.chatbot.saas.entity.*;
import com.chatbot.saas.repository.*;
import com.chatbot.saas.service.OAuthService;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** The endpoints and routes the dashboard relies on beyond the core API. */
@SpringBootTest
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardSupportIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private MessageRepository messageRepository;
    @MockBean private OAuthService oAuthService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private long businessId;
    private String apiKey;

    @BeforeEach
    void shop() throws Exception {
        JsonNode shop = json(mockMvc.perform(post("/api/businesses/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Shop", "email", "d" + System.nanoTime() + "@example.com"))))
                .andReturn().getResponse());
        businessId = shop.get("id").asLong();
        apiKey = shop.get("apiKey").asText();
    }

    private JsonNode json(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode getJson(String path) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get(path).header("X-API-Key", apiKey)).andReturn().getResponse();
        assertEquals(200, response.getStatus(), response.getContentAsString());
        return json(response);
    }

    private Customer customer(String psid, LocalDateTime lastInteraction) {
        Business business = businessRepository.findById(businessId).orElseThrow();
        return customerRepository.save(Customer.builder().business(business).facebookUserId(psid)
                .firstInteractionAt(lastInteraction).lastInteractionAt(lastInteraction).build());
    }

    private void message(Customer customer, Message.SenderType sender, String text) {
        messageRepository.save(Message.builder().business(customer.getBusiness()).customer(customer)
                .messageId("m-" + System.nanoTime()).direction(sender == Message.SenderType.CUSTOMER ? Message.Direction.INBOUND : Message.Direction.OUTBOUND)
                .senderType(sender).content(text).sentAt(LocalDateTime.now()).createdAt(LocalDateTime.now()).build());
    }

    private Order order(Customer customer, String name, Order.PaymentStatus paymentStatus, Order.Status status) {
        Business business = customer.getBusiness();
        Category category = categoryRepository.save(Category.builder().business(business).name("C").build());
        Product product = productRepository.save(Product.builder().business(business).category(category)
                .name("Rose").price(new BigDecimal("10000")).build());
        return orderRepository.save(Order.builder().business(business).customer(customer).product(product)
                .productName("Rose").unitPrice(new BigDecimal("10000")).quantity(1).totalAmount(new BigDecimal("10000"))
                .customerName(name).platform(Order.Platform.FACEBOOK).paymentStatus(paymentStatus).status(status).build());
    }

    @Test
    void chatListShowsNamesPreviewsAndMostRecentFirst() throws Exception {
        Customer older = customer("psid-old-" + System.nanoTime(), LocalDateTime.now().minusHours(3));
        Customer newer = customer("psid-new-" + System.nanoTime(), LocalDateTime.now().minusMinutes(5));
        message(older, Message.SenderType.CUSTOMER, "сайн уу");
        message(newer, Message.SenderType.CUSTOMER, "x".repeat(300));
        message(newer, Message.SenderType.BOT, "Та юу захиалах вэ?");
        order(newer, "Батбаяр", Order.PaymentStatus.NOT_REQUESTED, Order.Status.PENDING);

        JsonNode page = getJson("/api/businesses/" + businessId + "/chats");

        assertEquals(2, page.get("totalElements").asInt());
        JsonNode first = page.get("content").get(0);
        assertEquals(newer.getId(), first.get("customerId").asLong());
        assertEquals("Батбаяр", first.get("displayName").asText());
        assertEquals("BOT", first.get("lastMessageSender").asText());
        assertEquals("FACEBOOK", first.get("platform").asText());
        assertTrue(page.get("content").get(1).get("displayName").isNull(), "never ordered → no name");

        assertEquals(newer.getId(), getJson("/api/businesses/" + businessId + "/chats/" + newer.getId()).get("customerId").asLong());
    }

    @Test
    void waitingFilterListsOnlyHandoffsLongestWaitingFirst() throws Exception {
        customer("psid-idle-" + System.nanoTime(), LocalDateTime.now());
        Customer recent = customer("psid-w1-" + System.nanoTime(), LocalDateTime.now());
        recent.setHandoffRequestedAt(LocalDateTime.now().minusMinutes(1));
        customerRepository.save(recent);
        Customer longest = customer("psid-w2-" + System.nanoTime(), LocalDateTime.now().minusHours(2));
        longest.setHandoffRequestedAt(LocalDateTime.now().minusHours(1));
        customerRepository.save(longest);

        // Page size 1: the filter must apply server-side, not only to the rows already loaded
        JsonNode page = getJson("/api/businesses/" + businessId + "/chats?waiting=true&size=1");

        assertEquals(2, page.get("totalElements").asInt());
        assertEquals(longest.getId(), page.get("content").get(0).get("customerId").asLong());
        assertEquals(3, getJson("/api/businesses/" + businessId + "/chats").get("totalElements").asInt());
    }

    @Test
    void chatOfAnotherShopIsNotFound() throws Exception {
        JsonNode other = json(mockMvc.perform(post("/api/businesses/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Other", "email", "o" + System.nanoTime() + "@example.com"))))
                .andReturn().getResponse());
        Business otherBusiness = businessRepository.findById(other.get("id").asLong()).orElseThrow();
        Customer theirs = customerRepository.save(Customer.builder().business(otherBusiness).facebookUserId("p" + System.nanoTime())
                .firstInteractionAt(LocalDateTime.now()).lastInteractionAt(LocalDateTime.now()).build());

        assertEquals(404, mockMvc.perform(get("/api/businesses/" + businessId + "/chats/" + theirs.getId())
                .header("X-API-Key", apiKey)).andReturn().getResponse().getStatus());
    }

    @Test
    void ordersFilterByCustomerForTheChatPanel() throws Exception {
        Customer mine = customer("psid-a-" + System.nanoTime(), LocalDateTime.now());
        Customer other = customer("psid-b-" + System.nanoTime(), LocalDateTime.now());
        order(mine, "A", Order.PaymentStatus.NOT_REQUESTED, Order.Status.PENDING);
        order(mine, "A", Order.PaymentStatus.NOT_REQUESTED, Order.Status.DELIVERED);
        order(other, "B", Order.PaymentStatus.NOT_REQUESTED, Order.Status.PENDING);
        String base = "/api/businesses/" + businessId + "/orders?customerId=";

        JsonNode page = getJson(base + mine.getId());
        assertEquals(2, page.get("totalElements").asInt());
        page.get("content").forEach(o -> assertEquals(mine.getId(), o.get("customerId").asLong()));
        assertEquals(1, getJson(base + mine.getId() + "&status=PENDING").get("totalElements").asInt());
        assertEquals(3, getJson("/api/businesses/" + businessId + "/orders").get("totalElements").asInt());
    }

    @Test
    void shopSetsTheBotsGreetingAndDeliveryNote() throws Exception {
        String path = "/api/businesses/" + businessId;
        assertEquals(200, mockMvc.perform(put(path).header("X-API-Key", apiKey).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("welcomeMessage", " Тавтай морил 🌸 ", "deliveryNote", "1–2 хоногт"))))
                .andReturn().getResponse().getStatus());
        JsonNode shop = getJson(path);
        assertEquals("Тавтай морил 🌸", shop.get("welcomeMessage").asText());
        assertEquals("1–2 хоногт", shop.get("deliveryNote").asText());

        // Blank clears it; other fields are left alone when not sent
        mockMvc.perform(put(path).header("X-API-Key", apiKey).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("deliveryNote", "  "))));
        shop = getJson(path);
        assertTrue(shop.get("deliveryNote").isNull());
        assertEquals("Тавтай морил 🌸", shop.get("welcomeMessage").asText());

        assertEquals(400, mockMvc.perform(put(path).header("X-API-Key", apiKey).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("welcomeMessage", "x".repeat(501)))))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void summaryReportsQPayRevenueAndUnpaidInvoices() throws Exception {
        Customer c = customer("psid-" + System.nanoTime(), LocalDateTime.now());
        order(c, "A", Order.PaymentStatus.PAID, Order.Status.CONFIRMED);
        order(c, "B", Order.PaymentStatus.PENDING, Order.Status.PENDING);
        order(c, "C", Order.PaymentStatus.PENDING, Order.Status.CANCELLED);
        order(c, "D", Order.PaymentStatus.NOT_REQUESTED, Order.Status.PENDING);

        JsonNode summary = getJson("/api/businesses/" + businessId + "/analytics/summary");

        assertEquals(0, new BigDecimal("10000").compareTo(summary.get("paidRevenue").decimalValue()));
        assertEquals(1, summary.get("awaitingPaymentOrders").asInt(), "a cancelled order no longer awaits payment");
    }

    @Test
    void metaConnectReturnsTheOwnerToTheDashboard() throws Exception {
        Business business = businessRepository.findById(businessId).orElseThrow();
        business.setInstagramAccountId("ig");
        when(oAuthService.handleCallback(anyString(), anyString())).thenReturn(business);

        MockHttpServletResponse ok = mockMvc.perform(get("/api/auth/meta/callback").param("code", "c").param("state", "s"))
                .andReturn().getResponse();
        assertEquals(302, ok.getStatus());
        assertEquals("http://localhost:8080/settings?meta=connected", ok.getRedirectedUrl());

        when(oAuthService.handleCallback(anyString(), anyString())).thenThrow(new IllegalArgumentException("expired"));
        MockHttpServletResponse failed = mockMvc.perform(get("/api/auth/meta/callback").param("code", "c").param("state", "s"))
                .andReturn().getResponse();
        String location = URLDecoder.decode(failed.getRedirectedUrl(), StandardCharsets.UTF_8);
        assertTrue(location.startsWith("http://localhost:8080/settings?meta=failed&message="), location);

        MockHttpServletResponse cancelled = mockMvc.perform(get("/api/auth/meta/callback").param("error_description", "<script>"))
                .andReturn().getResponse();
        assertFalse(cancelled.getRedirectedUrl().contains("<"), "the message is URL-encoded, not injected");
    }

    @Test
    void dashboardRoutesAreServedWithoutLogin() throws Exception {
        // The dashboard is only bundled in a full build; either way these must not need auth
        for (String path : new String[]{"/", "/login", "/orders/5", "/invite/abc", "/settings/staff", "/chats/3"}) {
            int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertTrue(status == 200 || status == 404, path + " → " + status);
        }
        assertEquals(401, mockMvc.perform(get("/api/businesses/" + businessId + "/orders")).andReturn().getResponse().getStatus());
        int post = mockMvc.perform(post("/orders")).andReturn().getResponse().getStatus();
        assertTrue(post == 401 || post == 403, "pages are GET only, got " + post);
    }
}
