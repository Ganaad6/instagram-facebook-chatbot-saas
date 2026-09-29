package com.chatbot.saas.service;

import com.chatbot.saas.exception.QPayException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * QPay merchant API v2 (https://developer.qpay.mn). Every call runs with one shop's own
 * merchant credentials; access tokens are cached per merchant until shortly before they expire.
 */
@Service
@Slf4j
public class QPayClient {

    /** Refresh a cached token this long before QPay says it expires. */
    private static final long TOKEN_EXPIRY_MARGIN_SECONDS = 60;
    /** QPay reports expires_in as an absolute epoch second; anything smaller is a duration. */
    private static final long EPOCH_SECONDS_THRESHOLD = 1_000_000_000L;

    private final WebClient qpayWebClient;
    private final Map<String, CachedToken> tokens = new ConcurrentHashMap<>();

    public QPayClient(@Qualifier("qpayWebClient") WebClient qpayWebClient) {
        this.qpayWebClient = qpayWebClient;
    }

    public record Credentials(String username, String password, String invoiceCode) {
        @Override
        public String toString() {
            return "Credentials[username=" + username + ", invoiceCode=" + invoiceCode + "]";
        }
    }

    /** shortUrl opens QPay's page with the QR code and a button per bank app. */
    public record Invoice(String invoiceId, String shortUrl) {
    }

    /** paymentId is the first PAID payment, or null. */
    public record PaymentCheck(BigDecimal paidAmount, String paymentId) {
        public boolean covers(BigDecimal amount) {
            return paymentId != null && paidAmount.compareTo(amount) >= 0;
        }
    }

    private record CachedToken(String accessToken, long expiresAtEpochSecond) {
    }

    /** Logs in with the merchant credentials; throws with authenticationFailed=true if rejected. */
    public void verifyCredentials(String username, String password) {
        tokens.remove(username);
        token(new Credentials(username, password, null));
    }

    public Invoice createInvoice(Credentials credentials, String senderInvoiceNo, String description,
                                 BigDecimal amount, String callbackUrl) {
        Map<String, Object> body = Map.of(
                "invoice_code", credentials.invoiceCode(),
                "sender_invoice_no", senderInvoiceNo,
                "invoice_receiver_code", "terminal",
                "invoice_description", description,
                "amount", amount,
                "callback_url", callbackUrl);
        JsonNode response = authorized(credentials, token -> qpayWebClient.post()
                .uri("/invoice")
                .headers(h -> h.setBearerAuth(token))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block());
        if (response == null || !response.hasNonNull("invoice_id") || !response.hasNonNull("qPay_shortUrl")) {
            throw new QPayException("QPay did not return an invoice");
        }
        return new Invoice(response.get("invoice_id").asText(), response.get("qPay_shortUrl").asText());
    }

    public PaymentCheck checkPayment(Credentials credentials, String invoiceId) {
        Map<String, Object> body = Map.of(
                "object_type", "INVOICE",
                "object_id", invoiceId,
                "offset", Map.of("page_number", 1, "page_limit", 100));
        JsonNode response = authorized(credentials, token -> qpayWebClient.post()
                .uri("/payment/check")
                .headers(h -> h.setBearerAuth(token))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block());
        BigDecimal paid = BigDecimal.ZERO;
        String paymentId = null;
        if (response != null) {
            for (JsonNode row : response.path("rows")) {
                if ("PAID".equals(row.path("payment_status").asText())) {
                    paid = paid.add(new BigDecimal(row.path("payment_amount").asText("0")));
                    if (paymentId == null) {
                        paymentId = row.path("payment_id").asText(null);
                    }
                }
            }
        }
        return new PaymentCheck(paid, paymentId);
    }

    public void cancelInvoice(Credentials credentials, String invoiceId) {
        authorized(credentials, token -> qpayWebClient.delete()
                .uri("/invoice/{id}", invoiceId)
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block());
    }

    /** Runs the call with a cached token, logging in again once if QPay says it has expired. */
    private JsonNode authorized(Credentials credentials, Function<String, JsonNode> call) {
        try {
            return call.apply(token(credentials));
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() != HttpStatus.UNAUTHORIZED) {
                throw translate(e);
            }
            tokens.remove(credentials.username());
            try {
                return call.apply(token(credentials));
            } catch (WebClientResponseException retry) {
                throw translate(retry);
            } catch (WebClientRequestException retry) {
                throw unreachable(retry);
            }
        } catch (WebClientRequestException e) {
            throw unreachable(e);
        }
    }

    private String token(Credentials credentials) {
        long now = Instant.now().getEpochSecond();
        CachedToken cached = tokens.get(credentials.username());
        if (cached != null && cached.expiresAtEpochSecond() > now) {
            return cached.accessToken();
        }
        JsonNode response;
        try {
            response = qpayWebClient.post()
                    .uri("/auth/token")
                    .headers(h -> h.setBasicAuth(credentials.username(), credentials.password()))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new QPayException("QPay rejected the merchant username or password", true, e);
            }
            throw translate(e);
        } catch (WebClientRequestException e) {
            throw unreachable(e);
        }
        if (response == null || !response.hasNonNull("access_token")) {
            throw new QPayException("QPay did not return an access token");
        }
        long expiresIn = response.path("expires_in").asLong(0);
        long expiresAt = expiresIn > EPOCH_SECONDS_THRESHOLD ? expiresIn : now + expiresIn;
        String accessToken = response.get("access_token").asText();
        tokens.put(credentials.username(), new CachedToken(accessToken, expiresAt - TOKEN_EXPIRY_MARGIN_SECONDS));
        return accessToken;
    }

    private QPayException translate(WebClientResponseException e) {
        String detail = e.getStatusCode().toString();
        try {
            JsonNode body = e.getResponseBodyAs(JsonNode.class);
            if (body != null && body.hasNonNull("message")) {
                JsonNode message = body.get("message");
                detail += " " + (message.isTextual() ? message.asText() : message.toString());
            }
        } catch (Exception ignored) {
            // Fall back to the status alone
        }
        return new QPayException("QPay API error: " + detail, false, e);
    }

    private QPayException unreachable(WebClientRequestException e) {
        return new QPayException("Could not reach QPay", false, e);
    }
}
