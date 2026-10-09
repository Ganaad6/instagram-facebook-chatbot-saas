package com.chatbot.saas.service;

import com.chatbot.saas.exception.QPayException;
import com.chatbot.saas.service.QPayClient.Credentials;
import com.chatbot.saas.service.QPayClient.PaymentCheck;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the real WebClient request building against a stubbed transport, using response
 * shapes recorded from the QPay sandbox (merchant-sandbox.qpay.mn/v2).
 */
class QPayClientTest {

    private static final Credentials CREDENTIALS = new Credentials("SHOP_MERCHANT", "secret", "SHOP_INVOICE");

    /** Lets the stub serialize each request body the way the real connector would. */
    private static final BodyInserter.Context BODY_WRITERS = new BodyInserter.Context() {
        @Override
        public List<HttpMessageWriter<?>> messageWriters() {
            return ExchangeStrategies.withDefaults().messageWriters();
        }

        @Override
        public Optional<ServerHttpRequest> serverRequest() {
            return Optional.empty();
        }

        @Override
        public Map<String, Object> hints() {
            return Map.of();
        }
    };

    private final List<ClientRequest> requests = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private final Deque<ClientResponse> responses = new ArrayDeque<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final QPayClient client = new QPayClient(WebClient.builder()
            .baseUrl("https://qpay.example/v2")
            .exchangeFunction(request -> {
                requests.add(request);
                MockClientHttpRequest captured = new MockClientHttpRequest(request.method(), request.url());
                return request.body().insert(captured, BODY_WRITERS)
                        // Bodiless requests (token, DELETE) never set a body
                        .then(Mono.defer(captured::getBodyAsString).onErrorReturn(""))
                        .map(body -> {
                            bodies.add(body);
                            return responses.removeFirst();
                        });
            })
            .build());

    private void respond(HttpStatus status, String json) {
        responses.add(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(json)
                .build());
    }

    private void respondToken(String token, long expiresIn) {
        respond(HttpStatus.OK, "{\"token_type\":\"bearer\",\"access_token\":\"" + token + "\",\"expires_in\":" + expiresIn + "}");
    }

    private static final String INVOICE = """
            {"invoice_id":"inv-1","qr_text":"0002...","qr_image":"iVBOR...","qPay_shortUrl":"https://s.qpay.mn/abc",
             "urls":[{"name":"Khan bank","link":"khanbank://q?qPay_QRcode=0002"}]}""";

    @Test
    void createInvoiceLogsInOnceAndSendsTheInvoice() throws Exception {
        // QPay's expires_in is an absolute epoch second
        respondToken("t1", Instant.now().getEpochSecond() + 86_400);
        respond(HttpStatus.OK, INVOICE);
        respond(HttpStatus.OK, INVOICE);

        QPayClient.Invoice invoice = client.createInvoice(CREDENTIALS, "ORDER-7", "Захиалга #7",
                new BigDecimal("20000.00"), "https://shop.example/webhook/qpay/7?token=x");
        client.createInvoice(CREDENTIALS, "ORDER-8", "Захиалга #8", BigDecimal.TEN, "https://cb");

        assertEquals(new QPayClient.Invoice("inv-1", "https://s.qpay.mn/abc"), invoice);
        assertEquals(3, requests.size(), "the token is cached for the second invoice");
        assertEquals("/v2/auth/token", requests.get(0).url().getPath());
        assertEquals("Basic " + Base64.getEncoder().encodeToString("SHOP_MERCHANT:secret".getBytes()),
                requests.get(0).headers().getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("/v2/invoice", requests.get(1).url().getPath());
        assertEquals("Bearer t1", requests.get(1).headers().getFirst(HttpHeaders.AUTHORIZATION));
        JsonNode body = objectMapper.readTree(bodies.get(1));
        assertEquals("SHOP_INVOICE", body.get("invoice_code").asString());
        assertEquals("ORDER-7", body.get("sender_invoice_no").asString());
        assertEquals("terminal", body.get("invoice_receiver_code").asString());
        assertEquals(0, new BigDecimal("20000").compareTo(body.get("amount").decimalValue()));
        assertEquals("https://shop.example/webhook/qpay/7?token=x", body.get("callback_url").asString());
    }

    @Test
    void expiredTokenIsRenewedAndTheCallRetriedOnce() {
        respondToken("old", Instant.now().getEpochSecond() + 86_400);
        respond(HttpStatus.UNAUTHORIZED, "{\"error\":\"NO_CREDENTIALS\",\"message\":\"expired\"}");
        respondToken("new", 3600);
        respond(HttpStatus.OK, "{\"count\":0,\"rows\":[]}");

        client.checkPayment(CREDENTIALS, "inv-1");

        assertEquals(4, requests.size());
        assertEquals("Bearer new", requests.get(3).headers().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void checkPaymentAddsUpPaidRowsOnly() {
        respondToken("t", 3600);
        respond(HttpStatus.OK, """
                {"count":2,"paid_amount":150,"rows":[
                  {"payment_id":"p-1","payment_status":"PAID","payment_amount":"100.00","payment_currency":"MNT"},
                  {"payment_id":"p-2","payment_status":"FAILED","payment_amount":"50.00","payment_currency":"MNT"}]}""");

        PaymentCheck check = client.checkPayment(CREDENTIALS, "inv-1");

        assertEquals("p-1", check.paymentId());
        assertTrue(check.covers(new BigDecimal("100")));
        assertFalse(check.covers(new BigDecimal("150")));
        assertTrue(bodies.get(1).contains("\"object_type\":\"INVOICE\""));
        assertTrue(bodies.get(1).contains("\"object_id\":\"inv-1\""));
    }

    @Test
    void unpaidInvoiceCoversNothing() {
        respondToken("t", 3600);
        respond(HttpStatus.OK, "{\"count\":0,\"rows\":[]}");

        assertFalse(client.checkPayment(CREDENTIALS, "inv-1").covers(BigDecimal.ONE));
    }

    @Test
    void rejectedCredentialsAreReportedAsSuch() {
        respond(HttpStatus.UNAUTHORIZED, "{\"error\":\"AUTHENTICATION_FAILED\",\"message\":\"Нэвтрэх нэр, нууц үг буруу\"}");

        QPayException e = assertThrows(QPayException.class, () -> client.verifyCredentials("SHOP_MERCHANT", "wrong"));
        assertTrue(e.isAuthenticationFailed());
    }

    @Test
    void qpayValidationErrorsAreSurfaced() {
        respondToken("t", 3600);
        respond(HttpStatus.BAD_REQUEST, "{\"error\":{},\"message\":{\"invoice_code\":{\"type\":\"INVALID\"}}}");

        QPayException e = assertThrows(QPayException.class, () ->
                client.createInvoice(CREDENTIALS, "ORDER-1", "x", BigDecimal.ONE, "https://cb"));
        assertFalse(e.isAuthenticationFailed());
        assertTrue(e.getMessage().contains("invoice_code"), e.getMessage());
    }

    @Test
    void cancelInvoiceDeletesIt() {
        respondToken("t", 3600);
        respond(HttpStatus.OK, "{}");

        client.cancelInvoice(CREDENTIALS, "inv-1");

        assertEquals("DELETE", requests.get(1).method().name());
        assertEquals("/v2/invoice/inv-1", requests.get(1).url().getPath());
    }
}
