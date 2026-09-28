package com.chatbot.saas.service;

import com.chatbot.saas.dto.response.CustomerResponse;
import com.chatbot.saas.dto.response.MessageResponse;
import com.chatbot.saas.entity.Business;
import com.chatbot.saas.entity.Conversation;
import com.chatbot.saas.entity.Customer;
import com.chatbot.saas.exception.MessagingWindowClosedException;
import com.chatbot.saas.exception.MetaSendFailedException;
import com.chatbot.saas.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/**
 * Human handoff: lets a shop's staff take a conversation over from the bot.
 *
 * The bot is paused for one customer at a time (Customer.botPausedUntil). A pause starts when
 * the customer asks for a person, when staff reply (through this API or from the shop's own
 * Meta inbox), or when staff pause it explicitly. It ends when staff resume the bot, when the
 * customer types a menu keyword, or when the pause times out; ending it abandons the
 * half-finished bot conversation so the customer starts fresh.
 *
 * Staff replies must respect Meta's messaging windows: a normal reply within 24 hours of the
 * customer's last message, a HUMAN_AGENT-tagged reply within 7 days, nothing after that.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HandoffService {

    static final Set<String> HANDOFF_KEYWORDS = Set.of("оператор", "ажилтан", "хүн", "human", "agent", "operator");
    static final Duration STANDARD_WINDOW = Duration.ofHours(24);
    static final Duration HUMAN_AGENT_WINDOW = Duration.ofDays(7);
    static final int MAX_AGENT_MESSAGE_LENGTH = 2000;

    static final String HANDOFF_ACK = "Таны хүсэлтийг хүлээн авлаа 🙋 Манай ажилтан тантай удахгүй холбогдоно.\n"
            + "Ботоор үргэлжлүүлэх бол \"цэс\" гэж бичнэ үү.";

    private final CustomerService customerService;
    private final ConversationService conversationService;
    private final MessageLogService messageLogService;
    private final MetaReplyService metaReplyService;
    private final OAuthService oAuthService;
    private final OrderNotificationService notificationService;

    @Value("${chatbot.handoff-timeout-hours:12}")
    private long handoffTimeoutHours;

    public static boolean isHandoffRequest(String text) {
        return text != null && HANDOFF_KEYWORDS.contains(text.trim().toLowerCase(Locale.ROOT));
    }

    // ─── Called while processing a customer's message (customer row already locked) ───────

    /** The customer asked for a person: pause the bot, tell them, and alert the shop. */
    public void requestHandoff(Customer customer, Conversation conversation, String platform, String text) {
        LocalDateTime now = LocalDateTime.now();
        pause(customer, now);
        customer.setHandoffRequestedAt(now);

        String token = oAuthService.getDecryptedAccessToken(customer.getBusiness());
        if (token != null) {
            String messageId = metaReplyService.sendText(customer.getPlatformUserId(platform), HANDOFF_ACK, token);
            messageLogService.recordOutbound(conversation, messageId, HANDOFF_ACK);
        }
        Business business = customer.getBusiness();
        notificationService.notifyHandoffRequested(business.getNotificationWebhookUrl(), business.getId(),
                customer.getId(), platform, text);
        log.info("Customer {} asked for a person; bot paused", customer.getId());
    }

    /** Staff wrote to the customer from the shop's Meta inbox (seen as a message echo). */
    public void recordInboxReply(Customer customer, Conversation conversation, String messageId, String text) {
        messageLogService.recordAgent(customer, conversation, messageId, StringUtils.hasText(text) ? text : "[attachment]");
        pause(customer, LocalDateTime.now());
        customer.setHandoffRequestedAt(null);
        log.info("Staff replied to customer {} from the Meta inbox; bot paused", customer.getId());
    }

    /** The pause ran out: give the conversation back to the bot, starting fresh. */
    public void endHandoff(Customer customer) {
        customer.setBotPausedUntil(null);
        customer.setHandoffRequestedAt(null);
        conversationService.findActiveConversationForCustomer(customer.getId())
                .ifPresent(conversationService::abandonConversation);
    }

    /** The customer typed a menu keyword while paused: hand straight back to the bot. */
    public void resumeForCustomer(Customer customer) {
        customer.setBotPausedUntil(null);
        customer.setHandoffRequestedAt(null);
    }

    // ─── Staff API ────────────────────────────────────────────────────────────

    @Transactional
    public MessageResponse sendAgentMessage(Long businessId, Long customerId, String text) {
        if (!StringUtils.hasText(text)) {
            throw new ValidationException("text must not be empty");
        }
        if (text.length() > MAX_AGENT_MESSAGE_LENGTH) {
            throw new ValidationException("text must be at most " + MAX_AGENT_MESSAGE_LENGTH + " characters");
        }
        Customer customer = customerService.lockForBusiness(businessId, customerId);
        String token = oAuthService.getDecryptedAccessToken(customer.getBusiness());
        if (token == null) {
            throw new ValidationException("The business has not connected its Facebook Page yet");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastInbound = messageLogService.lastInboundAt(customerId)
                .orElseThrow(() -> new MessagingWindowClosedException(
                        "The customer has never written to the shop, so Meta doesn't allow messaging them"));
        Duration sinceCustomerWrote = Duration.between(lastInbound, now);
        if (sinceCustomerWrote.compareTo(HUMAN_AGENT_WINDOW) > 0) {
            throw new MessagingWindowClosedException(
                    "The customer last wrote more than 7 days ago; Meta only allows replies after they write again");
        }
        boolean humanAgentTag = sinceCustomerWrote.compareTo(STANDARD_WINDOW) > 0;

        String platform = customer.getPlatform();
        String messageId = metaReplyService.sendAgentText(customer.getPlatformUserId(platform), text, humanAgentTag, token);
        if (messageId == null) {
            throw new MetaSendFailedException("Meta did not accept the message; see the server log for details");
        }

        Conversation conversation = conversationService.findActiveConversationForCustomer(customerId).orElse(null);
        messageLogService.recordAgent(customer, conversation, messageId, text);
        // Keep the bot quiet while staff are talking to the customer
        pause(customer, now);
        customer.setHandoffRequestedAt(null);
        log.info("Staff message sent to customer {} (humanAgentTag={})", customerId, humanAgentTag);
        return messageLogService.getCustomerHistory(customerId, 1).get(0);
    }

    @Transactional
    public CustomerResponse pauseBot(Long businessId, Long customerId, Integer hours) {
        Customer customer = customerService.lockForBusiness(businessId, customerId);
        long pauseHours = hours != null ? hours : handoffTimeoutHours;
        if (pauseHours < 1 || pauseHours > 24 * 7) {
            throw new ValidationException("hours must be between 1 and 168");
        }
        customer.setBotPausedUntil(LocalDateTime.now().plusHours(pauseHours));
        return CustomerResponse.from(customer);
    }

    @Transactional
    public CustomerResponse resumeBot(Long businessId, Long customerId) {
        Customer customer = customerService.lockForBusiness(businessId, customerId);
        endHandoff(customer);
        return CustomerResponse.from(customer);
    }

    private void pause(Customer customer, LocalDateTime now) {
        LocalDateTime until = now.plusHours(handoffTimeoutHours);
        if (customer.getBotPausedUntil() == null || customer.getBotPausedUntil().isBefore(until)) {
            customer.setBotPausedUntil(until);
        }
    }
}
