package com.chatbot.saas.service;

import com.chatbot.saas.entity.*;
import com.chatbot.saas.repository.ChatbotFlowRepository;
import com.chatbot.saas.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageHandlerService {

    private final BusinessService businessService;
    private final CustomerService customerService;
    private final ConversationService conversationService;
    private final ChatbotFlowRepository chatbotFlowRepository;
    private final ConversationDataService conversationDataService;
    private final ValidationService validationService;
    private final MetaReplyService metaReplyService;
    private final MessageRepository messageRepository;
    private final ChatbotEngineService chatbotEngineService;
    private final OAuthService oAuthService;

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
        if (inbound.messageId() != null
                && messageRepository.existsByBusinessIdAndMessageId(business.getId(), inbound.messageId())) {
            log.info("Skipping already-processed message {}", inbound.messageId());
            return;
        }

        // 4. Find or create active conversation
        Conversation conversation = findActiveConversation(customer)
                .orElseGet(() -> startConversation(customer, business, inbound.platform()));

        // 5. Persist inbound message
        String content = StringUtils.hasText(inbound.text()) ? inbound.text() : "[attachment]";
        saveMessage(conversation, customer, business, inbound.messageId(), content, Message.Direction.INBOUND);

        // 6. Route to appropriate engine
        if (conversation.getFlow() != null) {
            handleLegacyFlowStep(conversation, customer, business, inbound.text(), inbound.senderId());
        } else {
            chatbotEngineService.process(conversation, inbound.text(), inbound.platform());
        }
    }

    private Optional<Business> resolveBusiness(InboundMessage inbound) {
        if ("FACEBOOK".equals(inbound.platform())) {
            return businessService.findByFacebookPageId(inbound.recipientId());
        }
        return businessService.findByInstagramAccountId(inbound.recipientId());
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
        // Legacy flow-based chatbot if configured, otherwise the product-order state machine
        return chatbotFlowRepository.findByBusinessIdAndIsActiveTrue(business.getId())
                .map(flow -> conversationService.createConversation(customer, business, flow, platform))
                .orElseGet(() -> conversationService.createStateMachineConversation(customer, business, platform));
    }

    // ─── Legacy Flow-Step Engine ──────────────────────────────────────────────

    private void handleLegacyFlowStep(Conversation conversation, Customer customer,
                                       Business business, String messageText, String senderId) {
        String accessToken = oAuthService.getDecryptedAccessToken(business);
        FlowStep currentStep = conversation.getCurrentStep();
        if (currentStep == null) {
            log.warn("No current step for conversation: {}", conversation.getId());
            conversationService.completeConversation(conversation);
            return;
        }

        boolean isValid = true;
        if (currentStep.getFieldName() != null && !currentStep.getFieldName().isEmpty()) {
            String validationType = currentStep.getValidationType() != null
                    ? currentStep.getValidationType().name() : "TEXT";
            isValid = validationService.validate(messageText, validationType, currentStep.getValidationRegex());

            if (isValid) {
                conversationDataService.saveData(conversation, currentStep.getFieldName(), messageText);
            } else {
                String errorMsg = currentStep.getErrorMessage() != null
                        ? currentStep.getErrorMessage() : "Invalid input. Please try again.";
                if (accessToken != null) {
                    metaReplyService.sendText(senderId, errorMsg, accessToken);
                    saveMessage(conversation, customer, business, null, errorMsg, Message.Direction.OUTBOUND);
                }
                return;
            }
        }

        FlowStep nextStep = currentStep.getNextStep();
        if (nextStep != null) {
            conversationService.updateConversationStep(conversation, nextStep);
            if (accessToken != null) {
                metaReplyService.sendText(senderId, nextStep.getMessageTemplate(), accessToken);
                saveMessage(conversation, customer, business, null, nextStep.getMessageTemplate(), Message.Direction.OUTBOUND);
            }
        } else {
            conversationService.completeConversation(conversation);
            String completionMsg = "Thank you! Your information has been recorded.";
            if (accessToken != null) {
                metaReplyService.sendText(senderId, completionMsg, accessToken);
                saveMessage(conversation, customer, business, null, completionMsg, Message.Direction.OUTBOUND);
            }
            log.info("Conversation {} completed for customer {}", conversation.getId(), customer.getId());
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void saveMessage(Conversation conversation, Customer customer, Business business,
                             String messageId, String content, Message.Direction direction) {
        Message message = Message.builder()
                .conversation(conversation)
                .customer(customer)
                .business(business)
                .messageId(messageId != null ? messageId : UUID.randomUUID().toString())
                .direction(direction)
                .content(content)
                .sentAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
        messageRepository.save(message);
    }
}
