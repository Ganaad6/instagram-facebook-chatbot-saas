package com.chatbot.saas.service;

import com.chatbot.saas.exception.WebhookAuthenticationException;
import com.chatbot.saas.service.MessageHandlerService.InboundMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebhookServiceTest {

    private static final String APP_SECRET = "test_app_secret";

    private MessageHandlerService messageHandlerService;
    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        messageHandlerService = mock(MessageHandlerService.class);
        webhookService = new WebhookService(messageHandlerService, new ObjectMapper());
        ReflectionTestUtils.setField(webhookService, "appSecret", APP_SECRET);
        ReflectionTestUtils.setField(webhookService, "verifyToken", "verify");
    }

    private static String sign(String payload) {
        return "sha256=" + new HmacUtils(HmacAlgorithms.HMAC_SHA_256, APP_SECRET).hmacHex(payload);
    }

    private InboundMessage deliver(String payload) {
        webhookService.processWebhookEvent(payload, sign(payload));
        ArgumentCaptor<InboundMessage> captor = ArgumentCaptor.forClass(InboundMessage.class);
        verify(messageHandlerService).handleIncomingMessage(captor.capture());
        return captor.getValue();
    }

    @Test
    void facebookQuickReplyUsesPayloadAndMid() {
        InboundMessage msg = deliver("""
                {"object":"page","entry":[{"messaging":[{"sender":{"id":"psid"},"recipient":{"id":"page-1"},
                 "message":{"mid":"m_1","text":"Shoes","quick_reply":{"payload":"2"}}}]}]}""");

        assertEquals(new InboundMessage("FACEBOOK", "psid", "page-1", "m_1", "2"), msg);
    }

    @Test
    void instagramTextMessage() {
        InboundMessage msg = deliver("""
                {"object":"instagram","entry":[{"messaging":[{"sender":{"id":"igsid"},"recipient":{"id":"ig-1"},
                 "message":{"mid":"m_2","text":"сайн уу"}}]}]}""");

        assertEquals(new InboundMessage("INSTAGRAM", "igsid", "ig-1", "m_2", "сайн уу"), msg);
    }

    @Test
    void attachmentOnlyMessageIsDispatchedWithEmptyText() {
        InboundMessage msg = deliver("""
                {"object":"instagram","entry":[{"messaging":[{"sender":{"id":"igsid"},"recipient":{"id":"ig-1"},
                 "message":{"mid":"m_3","attachments":[{"type":"image"}]}}]}]}""");

        assertEquals("", msg.text());
    }

    @Test
    void echoesAreIgnored() {
        String payload = """
                {"object":"page","entry":[{"messaging":[{"sender":{"id":"page-1"},"recipient":{"id":"psid"},
                 "message":{"mid":"m_4","text":"hi","is_echo":true}}]}]}""";
        webhookService.processWebhookEvent(payload, sign(payload));

        verify(messageHandlerService, never()).handleIncomingMessage(any());
    }

    @Test
    void invalidSignatureIsRejected() {
        assertThrows(WebhookAuthenticationException.class,
                () -> webhookService.processWebhookEvent("{}", "sha256=bogus"));
    }

    @Test
    void malformedPayloadWithValidSignatureDoesNotThrow() {
        // Throwing here would return a 5xx and make Meta redeliver the same bad payload forever
        assertDoesNotThrow(() -> webhookService.processWebhookEvent("not json", sign("not json")));
    }

    @Test
    void wrongVerifyTokenIsRejected() {
        assertThrows(WebhookAuthenticationException.class,
                () -> webhookService.verifyWebhook("subscribe", "wrong", "challenge"));
        assertEquals("challenge", webhookService.verifyWebhook("subscribe", "verify", "challenge"));
    }
}
