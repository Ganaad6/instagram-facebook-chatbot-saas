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

    @Async
    public void handleIncomingMessage(String senderId, String messageText, String recipientId) {
        log.debug("Handling message from {} to {}: {}", senderId, recipientId, messageText);

        // 1. Find business by Instagram/Page ID
        Optional<Business> businessOpt = businessService.findByInstagramAccountId(recipientId);
        if (businessOpt.isEmpty()) {
            businessOpt = businessService.findByFacebookPageId(recipientId);
        }
        if (businessOpt.isEmpty()) {
            log.warn("No business found for recipient ID: {}", recipientId);
            return;
        }
        Business business = businessOpt.get();

        // 2. Find or create customer
        Customer customer = customerService.findOrCreateCustomer(senderId, business);

        // 3. Save inbound message
        saveMessage(null, customer, business, messageText, Message.Direction.INBOUND);

        // 4. Find active conversation
        Optional<Conversation> activeConversation = conversationService.findActiveConversationForCustomer(customer.getId());
        Conversation conversation;

        if (activeConversation.isPresent()) {
            conversation = activeConversation.get();
        } else {
            // Find active flow for business
            Optional<ChatbotFlow> activeFlow = chatbotFlowRepository.findByBusinessIdAndIsActiveTrue(business.getId());
            if (activeFlow.isEmpty()) {
                log.warn("No active flow found for business: {}", business.getId());
                if (business.getAccessToken() != null) {
                    messageService.sendMessage(senderId, "Sorry, we're not available right now. Please try again later.", business.getAccessToken());
                }
                return;
            }
            conversation = conversationService.createConversation(customer, business, activeFlow.get());
        }

        // 5. Get current step
        FlowStep currentStep = conversation.getCurrentStep();
        if (currentStep == null) {
            log.warn("No current step for conversation: {}", conversation.getId());
            conversationService.completeConversation(conversation);
            return;
        }

        // 6. Validate input
        boolean isValid = true;
        if (currentStep.getFieldName() != null && !currentStep.getFieldName().isEmpty()) {
            String validationType = currentStep.getValidationType() != null
                    ? currentStep.getValidationType().name() : "TEXT";
            isValid = validationService.validate(messageText, validationType, currentStep.getValidationRegex());

            if (isValid) {
                // 7a. Save collected data
                conversationDataService.saveData(conversation, currentStep.getFieldName(), messageText);
            } else {
                // 7b. Send error message
                String errorMsg = currentStep.getErrorMessage() != null
                        ? currentStep.getErrorMessage()
                        : "Invalid input. Please try again.";
                if (business.getAccessToken() != null) {
                    messageService.sendMessage(senderId, errorMsg, business.getAccessToken());
                    saveMessage(conversation, customer, business, errorMsg, Message.Direction.OUTBOUND);
                }
                return;
            }
        }

        // 8. Advance to next step
        FlowStep nextStep = currentStep.getNextStep();
        if (nextStep != null) {
            conversationService.updateConversationStep(conversation, nextStep);
            if (business.getAccessToken() != null) {
                messageService.sendMessage(senderId, nextStep.getMessageTemplate(), business.getAccessToken());
                saveMessage(conversation, customer, business, nextStep.getMessageTemplate(), Message.Direction.OUTBOUND);
            }
        } else {
            // Flow complete
            conversationService.completeConversation(conversation);
            String completionMsg = "Thank you! Your information has been recorded.";
            if (business.getAccessToken() != null) {
                messageService.sendMessage(senderId, completionMsg, business.getAccessToken());
                saveMessage(conversation, customer, business, completionMsg, Message.Direction.OUTBOUND);
            }
            log.info("Conversation {} completed for customer {}", conversation.getId(), customer.getId());
        }
    }

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
