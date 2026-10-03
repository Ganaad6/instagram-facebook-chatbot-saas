package com.chatbot.saas.service;

import com.chatbot.saas.dto.response.MessageResponse;
import com.chatbot.saas.entity.Business;
import com.chatbot.saas.entity.Conversation;
import com.chatbot.saas.entity.Customer;
import com.chatbot.saas.entity.Message;
import com.chatbot.saas.exception.MessagingWindowClosedException;
import com.chatbot.saas.exception.MetaSendFailedException;
import com.chatbot.saas.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HandoffServiceTest {

    private CustomerService customerService;
    private ConversationService conversationService;
    private MessageLogService messageLogService;
    private MetaReplyService metaReplyService;
    private OAuthService oAuthService;
    private OrderNotificationService notificationService;
    private HandoffService handoffService;
    private Customer customer;

    @BeforeEach
    void setUp() {
        customerService = mock(CustomerService.class);
        conversationService = mock(ConversationService.class);
        messageLogService = mock(MessageLogService.class);
        metaReplyService = mock(MetaReplyService.class);
        oAuthService = mock(OAuthService.class);
        notificationService = mock(OrderNotificationService.class);
        handoffService = new HandoffService(customerService, conversationService, messageLogService,
                metaReplyService, oAuthService, notificationService);
        ReflectionTestUtils.setField(handoffService, "handoffTimeoutHours", 12L);

        Business business = Business.builder().id(1L).notificationWebhookUrl("https://shop/hook").build();
        customer = Customer.builder().id(5L).business(business).facebookUserId("psid-5").build();
        when(customerService.lockForBusiness(1L, 5L)).thenReturn(customer);
        when(oAuthService.getDecryptedAccessToken(business)).thenReturn("token");
        when(conversationService.findActiveConversationForCustomer(5L)).thenReturn(Optional.empty());
        when(messageLogService.getCustomerHistory(5L, 1)).thenReturn(List.of(
                MessageResponse.builder().senderType(Message.SenderType.AGENT).build()));
    }

    private void customerLastWrote(LocalDateTime when) {
        when(messageLogService.lastInboundAt(5L)).thenReturn(Optional.of(when));
    }

    @Test
    void replyWithin24HoursIsAStandardResponseAndPausesTheBot() {
        customerLastWrote(LocalDateTime.now().minusHours(2));
        customer.setHandoffRequestedAt(LocalDateTime.now().minusHours(1));
        when(metaReplyService.sendAgentText("psid-5", "Сайн байна уу", false, "token")).thenReturn("m_agent");

        handoffService.sendAgentMessage(1L, 5L, "Сайн байна уу");

        verify(messageLogService).recordAgent(customer, null, "m_agent", "Сайн байна уу");
        assertTrue(customer.isBotPaused(LocalDateTime.now().plusHours(11)));
        assertNull(customer.getHandoffRequestedAt(), "answered, so no longer waiting");
    }

    @Test
    void replyBetween24HoursAnd7DaysUsesHumanAgentTag() {
        customerLastWrote(LocalDateTime.now().minusDays(3));
        when(metaReplyService.sendAgentText(anyString(), anyString(), eq(true), anyString())).thenReturn("m_agent");

        handoffService.sendAgentMessage(1L, 5L, "Your order shipped");

        verify(metaReplyService).sendAgentText("psid-5", "Your order shipped", true, "token");
    }

    @Test
    void replyAfter7DaysIsRefused() {
        customerLastWrote(LocalDateTime.now().minusDays(8));

        assertThrows(MessagingWindowClosedException.class, () -> handoffService.sendAgentMessage(1L, 5L, "hi"));
        verifyNoInteractions(metaReplyService);
    }

    @Test
    void customerWhoNeverWroteCannotBeMessaged() {
        when(messageLogService.lastInboundAt(5L)).thenReturn(Optional.empty());

        assertThrows(MessagingWindowClosedException.class, () -> handoffService.sendAgentMessage(1L, 5L, "hi"));
    }

    @Test
    void metaRejectionIsReportedAndNothingIsRecorded() {
        customerLastWrote(LocalDateTime.now().minusHours(1));
        when(metaReplyService.sendAgentText(anyString(), anyString(), anyBoolean(), anyString())).thenReturn(null);

        assertThrows(MetaSendFailedException.class, () -> handoffService.sendAgentMessage(1L, 5L, "hi"));
        verify(messageLogService, never()).recordAgent(any(), any(), any(), any());
    }

    @Test
    void blankMessageIsRejected() {
        assertThrows(ValidationException.class, () -> handoffService.sendAgentMessage(1L, 5L, "  "));
    }

    @Test
    void handoffRequestPausesBotAcknowledgesAndNotifiesShop() {
        Conversation conversation = Conversation.builder().id(9L).customer(customer).business(customer.getBusiness()).build();
        when(metaReplyService.sendText(eq("psid-5"), anyString(), eq("token"))).thenReturn("m_ack");

        handoffService.requestHandoff(customer, conversation, "FACEBOOK", "оператор");

        assertTrue(customer.isBotPaused(LocalDateTime.now()));
        assertNotNull(customer.getHandoffRequestedAt());
        verify(messageLogService).recordOutbound(conversation, "m_ack", HandoffService.HANDOFF_ACK);
        verify(notificationService).notifyHandoffRequested("https://shop/hook", 1L, 5L, "FACEBOOK", "оператор");
    }

    @Test
    void resumeAbandonsTheHalfFinishedBotConversation() {
        customer.setBotPausedUntil(LocalDateTime.now().plusHours(5));
        Conversation active = Conversation.builder().id(9L).build();
        when(conversationService.findActiveConversationForCustomer(5L)).thenReturn(Optional.of(active));

        handoffService.resumeBot(1L, 5L);

        assertNull(customer.getBotPausedUntil());
        verify(conversationService).abandonConversation(active);
    }

    @Test
    void pauseHoursAreBounded() {
        assertThrows(ValidationException.class, () -> handoffService.pauseBot(1L, 5L, 0));
        assertThrows(ValidationException.class, () -> handoffService.pauseBot(1L, 5L, 169));
        handoffService.pauseBot(1L, 5L, 48);
        assertTrue(customer.isBotPaused(LocalDateTime.now().plusHours(47)));
    }

    @Test
    void handoffKeywordsMatchWholeMessageOnly() {
        assertTrue(HandoffService.isHandoffRequest(" Оператор "));
        assertTrue(HandoffService.isHandoffRequest("human"));
        assertFalse(HandoffService.isHandoffRequest("хүн бүрт хямдрал байна уу"));
        assertFalse(HandoffService.isHandoffRequest(null));
    }
}
