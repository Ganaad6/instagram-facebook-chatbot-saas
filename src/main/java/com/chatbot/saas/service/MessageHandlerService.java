package com.chatbot.saas.service;

import com.chatbot.saas.entity.*;
import com.chatbot.saas.repository.ChatbotFlowRepository;
import com.chatbot.saas.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final MessageService messageService;
    private final MessageRepository messageRepository;
    private final ChatbotEngineService chatbotEngineService;
    private final OAuthService oAuthService;

    /**
     * Handle a text message (Instagram or Facebook plain-text).
     * Uses the product-order state machine when no legacy flow is active.
     */
    @Async
    @Transactional
    public void handleIncomingMessage(String senderId, String messageText, String recipientId) {
        handleIncomingMessage(senderId, messageText, recipientId, "INSTAGRAM");
    }

    @Async
    @Transactional
    public void handleIncomingMessage(String senderId, String messageText, String recipientId, String platform) {
        log.debug("Handling message from {} to {} [{}]: {}", senderId, recipientId, platform, messageText);

        // 1. Resolve business
        Optional<Business> businessOpt = businessService.findByInstagramAccountId(recipientId);
        if (businessOpt.isEmpty()) {
            businessOpt = businessService.findByFacebookPageId(recipientId);
        }
        if (businessOpt.isEmpty()) {
            log.warn("No business found for recipient ID: {}", recipientId);
            return;
        }
        Business business = businessOpt.get();
        if (business.getStatus() != Business.Status.ACTIVE) {
            log.warn("Ignoring message for suspended business: {}", business.getId());
            return;
        }

        // 2. Find or create customer
        Customer customer = customerService.findOrCreateCustomer(senderId, business);

        // 3. Persist inbound message
        saveMessage(null, customer, business, messageText, Message.Direction.INBOUND);

        // 4. Find or create active conversation
        Optional<Conversation> activeConversation = conversationService.findActiveConversationForCustomer(customer.getId());
        Conversation conversation;

        if (activeConversation.isPresent()) {
            conversation = activeConversation.get();
        } else {
            // Check if a legacy flow-based chatbot is configured
            Optional<ChatbotFlow> activeFlow = chatbotFlowRepository.findByBusinessIdAndIsActiveTrue(business.getId());
            if (activeFlow.isPresent()) {
                conversation = conversationService.createConversation(customer, business, activeFlow.get());
            } else {
                // Default: product-order state machine
                conversation = conversationService.createStateMachineConversation(customer, business, platform);
            }
        }

        // 5. Route to appropriate engine
        if (conversation.getFlow() != null) {
            // Legacy flow-step engine
            handleLegacyFlowStep(conversation, customer, business, messageText, senderId);
        } else {
            // Product-order state machine engine
            chatbotEngineService.process(conversation, messageText, platform);
        }
    }

    /**
     * Handle a Facebook postback (button click).
     * The payload is treated as the user's text input for the state machine.
     */
    @Async
    @Transactional
    public void handlePostback(String senderId, String payload, String recipientId) {
        log.debug("Handling postback from {} payload={}", senderId, payload);
        handleIncomingMessage(senderId, payload, recipientId, "FACEBOOK");
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
                    messageService.sendMessage(senderId, errorMsg, accessToken);
                    saveMessage(conversation, customer, business, errorMsg, Message.Direction.OUTBOUND);
                }
                return;
            }
        }

        FlowStep nextStep = currentStep.getNextStep();
        if (nextStep != null) {
            conversationService.updateConversationStep(conversation, nextStep);
            if (accessToken != null) {
                messageService.sendMessage(senderId, nextStep.getMessageTemplate(), accessToken);
                saveMessage(conversation, customer, business, nextStep.getMessageTemplate(), Message.Direction.OUTBOUND);
            }
        } else {
            conversationService.completeConversation(conversation);
            String completionMsg = "Thank you! Your information has been recorded.";
            if (accessToken != null) {
                messageService.sendMessage(senderId, completionMsg, accessToken);
                saveMessage(conversation, customer, business, completionMsg, Message.Direction.OUTBOUND);
            }
            log.info("Conversation {} completed for customer {}", conversation.getId(), customer.getId());
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void saveMessage(Conversation conversation, Customer customer, Business business,
                             String content, Message.Direction direction) {
        Message message = Message.builder()
                .conversation(conversation)
                .customer(customer)
                .business(business)
                .messageId(UUID.randomUUID().toString())
                .direction(direction)
                .content(content)
                .sentAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
        messageRepository.save(message);
    }
}
