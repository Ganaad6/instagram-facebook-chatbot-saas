package com.chatbot.saas.service;

import com.chatbot.saas.entity.*;
import com.chatbot.saas.exception.QPayException;
import com.chatbot.saas.repository.*;
import com.chatbot.saas.service.MessageHandlerService.InboundMessage;
import com.chatbot.saas.service.QPayClient.Credentials;
import com.chatbot.saas.service.QPayClient.Invoice;
import com.chatbot.saas.service.QPayClient.PaymentCheck;
import com.chatbot.saas.util.EncryptionUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QPay payments end to end: a chat order through the real handler and database, QPay's
 * callback over HTTP, and the shop's payment API. Only QPay and Meta are faked.
 */
@SpringBootTest
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentIntegrationTest {

    private static final String PAY_URL = "https://s.qpay.mn/abc";

    @Autowired private MockMvc mockMvc;
    @Autowired private MessageHandlerService messageHandlerService;
    @Autowired private PaymentService paymentService;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private EncryptionUtil encryptionUtil;
    @Autowired private ThreadPoolTaskExecutor taskExecutor;

    @MockBean private MetaReplyService metaReplyService;
    @MockBean private QPayClient qpayClient;

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
        JsonNode registered = register(pageId);
        businessId = registered.get("id").asLong();
        apiKey = registered.get("apiKey").asText();

        Business business = businessRepository.findById(businessId).orElseThrow();
        business.setFacebookPageId(pageId);
        business.setAccessToken(encryptionUtil.encrypt("page-token"));
        businessRepository.save(business);
        Category category = categoryRepository.save(Category.builder().business(business).name("Shoes").build());
        productRepository.save(Product.builder().business(business).category(category)
                .name("Red shoes").price(new BigDecimal("10000.00")).build());

        when(metaReplyService.sendText(anyString(), anyString(), anyString())).thenAnswer(inv -> "m_bot_" + mids.incrementAndGet());
        when(metaReplyService.sendMenuMessage(anyString(), anyString(), anyString(), anyList(), anyString()))
                .thenAnswer(inv -> "m_bot_" + mids.incrementAndGet());
        when(metaReplyService.sendWithQuickReplies(anyString(), anyString(), anyList(), anyString()))
                .thenAnswer(inv -> "m_bot_" + mids.incrementAndGet());
        when(qpayClient.createInvoice(any(), anyString(), anyString(), any(), anyString()))
                .thenReturn(new Invoice("inv-" + pageId, PAY_URL));
        when(qpayClient.checkPayment(any(), anyString())).thenReturn(new PaymentCheck(BigDecimal.ZERO, null));
    }

    private JsonNode register(String unique) throws Exception {
        String response = mockMvc.perform(post("/api/businesses/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Shop", "email", unique + "@example.com"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private void connectQPay() throws Exception {
        mockMvc.perform(put("/api/businesses/" + businessId + "/payments/qpay")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "SHOP_MERCHANT", "password", "qpay-secret", "invoiceCode", "SHOP_INVOICE"))))
                .andExpect(status().isOk());
    }

    /** Walks the bot's menu to a placed order of 2 × Red shoes. */
    private Order placeOrder() throws InterruptedException {
        for (String text : List.of("сайн уу", "1", "1", "2", "1", "Бат", "99112233", "Хан-Уул, 3-р хороо")) {
            messageHandlerService.handleIncomingMessage(
                    new InboundMessage("FACEBOOK", psid, pageId, "m_in_" + mids.incrementAndGet(), text));
            awaitIdle();
        }
        List<Order> orders = orderRepository.findAllByBusinessIdAndCreatedAtBetween(
                businessId, java.time.LocalDateTime.now().minusMinutes(5), java.time.LocalDateTime.now().plusMinutes(1));
        assertEquals(1, orders.size());
        return orders.get(0);
    }

    private String capturedCallbackUrl() {
        ArgumentCaptor<String> callback = ArgumentCaptor.forClass(String.class);
        verify(qpayClient).createInvoice(any(), anyString(), anyString(), any(), callback.capture());
        return callback.getValue();
    }

    private Order reload(Order order) {
        return orderRepository.findById(order.getId()).orElseThrow();
    }

    @Test
    void chatOrderGetsAPaymentLinkAndTheCallbackRecordsThePayment() throws Exception {
        connectQPay();
        Business business = businessRepository.findById(businessId).orElseThrow();
        assertNotEquals("qpay-secret", business.getQpayPassword(), "password is encrypted at rest");

        Order order = placeOrder();

        assertEquals(Order.PaymentStatus.PENDING, order.getPaymentStatus());
        assertEquals(PAY_URL, order.getPaymentUrl());
        verify(qpayClient).createInvoice(eq(new Credentials("SHOP_MERCHANT", "qpay-secret", "SHOP_INVOICE")),
                eq("ORDER-" + order.getId()), contains("Red shoes × 2"),
                argThat(amount -> amount.compareTo(new BigDecimal("20000")) == 0), anyString());
        verify(metaReplyService).sendText(eq(psid), contains(PAY_URL), eq("page-token"));

        URI callback = URI.create(capturedCallbackUrl());
        assertTrue(callback.toString().startsWith("https://shop.example/webhook/qpay/" + order.getId() + "?token="));

        // A forged callback doesn't even make us ask QPay
        mockMvc.perform(get("/webhook/qpay/" + order.getId()).param("token", "forged"))
                .andExpect(status().isOk());
        verify(qpayClient, never()).checkPayment(any(), anyString());

        // Callback arrives but QPay says unpaid: nothing changes
        mockMvc.perform(get(callback.getPath() + "?" + callback.getQuery())).andExpect(status().isOk());
        assertEquals(Order.PaymentStatus.PENDING, reload(order).getPaymentStatus());

        // Paid
        when(qpayClient.checkPayment(any(), eq("inv-" + pageId))).thenReturn(new PaymentCheck(new BigDecimal("20000.00"), "pay-1"));
        mockMvc.perform(post(callback.getPath() + "?" + callback.getQuery())).andExpect(status().isOk());

        Order paid = reload(order);
        assertEquals(Order.PaymentStatus.PAID, paid.getPaymentStatus());
        assertEquals("pay-1", paid.getQpayPaymentId());
        assertNotNull(paid.getPaidAt());
        assertEquals(Order.Status.PENDING, paid.getStatus(), "fulfilment status is the shop's to change");
        verify(metaReplyService).sendText(eq(psid), contains("төлөгдлөө"), eq("page-token"));
        assertTrue(messageRepository.findAllByBusinessId(businessId).stream()
                .anyMatch(m -> m.getContent().contains("төлөгдлөө")), "confirmation is in the transcript");

        // QPay redelivers the callback: recorded once, customer told once
        mockMvc.perform(get(callback.getPath() + "?" + callback.getQuery())).andExpect(status().isOk());
        verify(metaReplyService, times(1)).sendText(eq(psid), contains("төлөгдлөө"), anyString());

        String json = mockMvc.perform(get("/api/businesses/" + businessId + "/orders/" + order.getId())
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals("PAID", objectMapper.readTree(json).get("paymentStatus").asText());
    }

    @Test
    void underpaymentIsNotRecordedAsPaid() throws Exception {
        connectQPay();
        Order order = placeOrder();
        when(qpayClient.checkPayment(any(), anyString())).thenReturn(new PaymentCheck(new BigDecimal("10000"), "pay-1"));

        URI callback = URI.create(capturedCallbackUrl());
        mockMvc.perform(get(callback.getPath() + "?" + callback.getQuery())).andExpect(status().isOk());

        assertEquals(Order.PaymentStatus.PENDING, reload(order).getPaymentStatus());
    }

    @Test
    void shopWithoutQPayGetsTheUsualConfirmation() throws Exception {
        Order order = placeOrder();

        assertEquals(Order.PaymentStatus.NOT_REQUESTED, order.getPaymentStatus());
        verify(qpayClient, never()).createInvoice(any(), anyString(), anyString(), any(), anyString());
        verify(metaReplyService).sendText(eq(psid), contains("удахгүй холбогдоно"), anyString());
    }

    @Test
    void qpayOutageStillSavesTheOrder() throws Exception {
        connectQPay();
        when(qpayClient.createInvoice(any(), anyString(), anyString(), any(), anyString()))
                .thenThrow(new QPayException("Could not reach QPay"));

        Order order = placeOrder();

        assertEquals(Order.PaymentStatus.NOT_REQUESTED, order.getPaymentStatus());
        verify(metaReplyService).sendText(eq(psid), contains("удахгүй холбогдоно"), anyString());
    }

    @Test
    void rejectedCredentialsAreNotStored() throws Exception {
        doThrow(new QPayException("rejected", true, null)).when(qpayClient).verifyCredentials("SHOP_MERCHANT", "wrong");

        mockMvc.perform(put("/api/businesses/" + businessId + "/payments/qpay")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "SHOP_MERCHANT", "password", "wrong", "invoiceCode", "SHOP_INVOICE"))))
                .andExpect(status().isBadRequest());

        assertFalse(businessRepository.findById(businessId).orElseThrow().isQpayConnected());
    }

    @Test
    void anotherShopCannotConfigureOrCheckPayments() throws Exception {
        connectQPay();
        Order order = placeOrder();
        String otherKey = register("other-" + System.nanoTime()).get("apiKey").asText();

        mockMvc.perform(put("/api/businesses/" + businessId + "/payments/qpay")
                        .header("X-API-Key", otherKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "EVIL", "password", "x", "invoiceCode", "EVIL"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/businesses/" + businessId + "/orders/" + order.getId() + "/payment/check")
                        .header("X-API-Key", otherKey))
                .andExpect(status().isForbidden());
        assertEquals("SHOP_MERCHANT", businessRepository.findById(businessId).orElseThrow().getQpayUsername());
    }

    @Test
    void shopCanCheckAPaymentManually() throws Exception {
        connectQPay();
        Order order = placeOrder();
        when(qpayClient.checkPayment(any(), anyString())).thenReturn(new PaymentCheck(new BigDecimal("20000"), "pay-9"));

        String json = mockMvc.perform(post("/api/businesses/" + businessId + "/orders/" + order.getId() + "/payment/check")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertEquals("PAID", objectMapper.readTree(json).get("paymentStatus").asText());
    }

    @Test
    void reconcileCatchesAMissedCallback() throws Exception {
        connectQPay();
        Order order = placeOrder();
        when(qpayClient.checkPayment(any(), anyString())).thenReturn(new PaymentCheck(new BigDecimal("20000"), "pay-2"));

        paymentService.reconcilePendingPayments();

        assertEquals(Order.PaymentStatus.PAID, reload(order).getPaymentStatus());
    }

    @Test
    void cancellingAnUnpaidOrderWithdrawsTheInvoice() throws Exception {
        connectQPay();
        Order order = placeOrder();

        mockMvc.perform(put("/api/businesses/" + businessId + "/orders/" + order.getId() + "/status")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());

        verify(qpayClient).cancelInvoice(any(), eq("inv-" + pageId));
        Order cancelled = reload(order);
        assertEquals(Order.Status.CANCELLED, cancelled.getStatus());
        assertEquals(Order.PaymentStatus.NOT_REQUESTED, cancelled.getPaymentStatus());
    }

    @Test
    void disconnectingStopsNewInvoices() throws Exception {
        connectQPay();
        mockMvc.perform(delete("/api/businesses/" + businessId + "/payments/qpay").header("X-API-Key", apiKey))
                .andExpect(status().isOk());

        Order order = placeOrder();

        assertEquals(Order.PaymentStatus.NOT_REQUESTED, order.getPaymentStatus());
        String json = mockMvc.perform(get("/api/businesses/" + businessId).header("X-API-Key", apiKey))
                .andReturn().getResponse().getContentAsString();
        assertFalse(objectMapper.readTree(json).get("qpayConnected").asBoolean());
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
