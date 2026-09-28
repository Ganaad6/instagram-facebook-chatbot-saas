package com.chatbot.saas.service;

import com.chatbot.saas.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageHandlerService {

    private final BusinessService businessService;
    private final CustomerService customerService;
    private final ConversationService conversationService;
    private final MessageLogService messageLogService;
    private final ChatbotEngineService chatbotEngineService;
    private final HandoffService handoffService;

    @Value("${meta.app.id:}")
    private String metaAppId;

    @Value("${chatbot.conversation-timeout-hours:24}")
    private long conversationTimeoutHours;

    /**
     * A customer message or button click from a Meta webhook.
     *
     * @param platform    "FACEBOOK" or "INSTAGRAM"
     * @param messageId   Meta's message id (mid), used to skip redelivered webhooks; may be null
     * @param text        the text, or quick-reply/postback payload; empty for attachment-only messages
     */
    public record InboundMessage(String platform, String senderId, String recipientId,
                                 String messageId, String text) {
    }

    /**
     * Meta's copy of a message the shop's Page / Instagram account sent - either one this app
     * sent, or one a staff member typed in the shop's Meta inbox.
     *
     * @param accountId  the shop's Page / Instagram account (the echo's sender)
     * @param customerId the customer it was sent to (the echo's recipient)
     * @param appId      the app that sent it, if Meta says; null or another app for inbox replies
     */
    public record EchoMessage(String platform, String accountId, String customerId,
                              String messageId, String text, String appId) {
    }

    /**
     * Runs off the webhook thread so Meta gets its 200 immediately. The customer row lock taken
     * in findOrCreateAndLock is held until this transaction commits, so a customer's messages are
     * handled strictly in sequence even though several may arrive at once.
     */
    @Async
    @Transactional
    public void handleIncomingMessage(InboundMessage inbound) {
        log.debug("Handling message from {} to {} [{}]", inbound.senderId(), inbound.recipientId(), inbound.platform());

        // 1. Resolve business
        Optional<Business> businessOpt = resolveBusiness(inbound);
        if (businessOpt.isEmpty()) {
            log.warn("No business found for recipient ID: {}", inbound.recipientId());
            return;
        }
        Business business = businessOpt.get();
        if (business.getStatus() != Business.Status.ACTIVE) {
            log.warn("Ignoring message for suspended business: {}", business.getId());
            return;
        }

        // 2. Find or create customer, and serialize this customer's messages from here on
        Customer customer = customerService.findOrCreateAndLock(business, inbound.senderId(), inbound.platform());

        // 3. Skip webhook redeliveries (checked under the customer lock, so no race)
        if (messageLogService.isAlreadyRecorded(business.getId(), inbound.messageId())) {
            log.info("Skipping already-processed message {}", inbound.messageId());
            return;
        }

        // 4. A staff pause that has run out hands the customer back to the bot, starting fresh
        LocalDateTime now = LocalDateTime.now();
        if (customer.getBotPausedUntil() != null && !customer.isBotPaused(now)) {
            handoffService.endHandoff(customer);
        }

        // 5. Find or create active conversation
        Conversation conversation = findActiveConversation(customer)
                .orElseGet(() -> startConversation(customer, business, inbound.platform()));

        // 6. Persist inbound message
        String content = StringUtils.hasText(inbound.text()) ? inbound.text() : "[attachment]";
        messageLogService.recordInbound(conversation, inbound.messageId(), content);

        // 7. Human handoff: while staff have the conversation the bot stays silent, unless the
        //    customer asks for the menu again
        if (customer.isBotPaused(now)) {
            if (!ChatbotEngineService.isRestartKeyword(inbound.text())) {
                log.debug("Bot paused for customer {}; leaving message for staff", customer.getId());
                return;
            }
            handoffService.resumeForCustomer(customer);
        } else if (HandoffService.isHandoffRequest(inbound.text())) {
            handoffService.requestHandoff(customer, conversation, inbound.platform(), inbound.text());
            return;
        }

        // 8. Let the bot answer
        chatbotEngineService.process(conversation, inbound.text(), inbound.platform());
    }

    /**
     * Echoes of the app's own sends are ignored. Anything else is a staff member replying from
     * the shop's Meta inbox: record it in the transcript and pause the bot so it doesn't talk
     * over them.
     */
    @Async
    @Transactional
    public void handleEcho(EchoMessage echo) {
        if (StringUtils.hasText(metaAppId) && metaAppId.equals(echo.appId())) {
            return; // sent by this app
        }
        Optional<Business> businessOpt = resolveBusiness(echo.platform(), echo.accountId());
        if (businessOpt.isEmpty() || businessOpt.get().getStatus() != Business.Status.ACTIVE) {
            return;
        }
        Business business = businessOpt.get();
        Customer customer = customerService.findOrCreateAndLockWithoutTouching(business, echo.customerId(), echo.platform());
        // Checked under the customer lock: a bot reply is recorded in the same transaction that
        // holds this lock, so its echo can't slip past this check. Also covers redeliveries.
        if (messageLogService.isAlreadyRecorded(business.getId(), echo.messageId())) {
            return;
        }
        Conversation conversation = conversationService.findActiveConversationForCustomer(customer.getId()).orElse(null);
        handoffService.recordInboxReply(customer, conversation, echo.messageId(), echo.text());
    }

    private Optional<Business> resolveBusiness(InboundMessage inbound) {
        return resolveBusiness(inbound.platform(), inbound.recipientId());
    }

    private Optional<Business> resolveBusiness(String platform, String accountId) {
        if ("FACEBOOK".equals(platform)) {
            return businessService.findByFacebookPageId(accountId);
        }
        return businessService.findByInstagramAccountId(accountId);
    }

    /** An active conversation idle past the timeout is abandoned so the customer starts fresh. */
    private Optional<Conversation> findActiveConversation(Customer customer) {
        Optional<Conversation> active = conversationService.findActiveConversationForCustomer(customer.getId());
        if (active.isPresent()
                && active.get().getUpdatedAt().isBefore(LocalDateTime.now().minusHours(conversationTimeoutHours))) {
            log.info("Abandoning stale conversation {}", active.get().getId());
            conversationService.abandonConversation(active.get());
            return Optional.empty();
        }
        return active;
    }

    private Conversation startConversation(Customer customer, Business business, String platform) {
        return conversationService.createStateMachineConversation(customer, business, platform);
    }
}
