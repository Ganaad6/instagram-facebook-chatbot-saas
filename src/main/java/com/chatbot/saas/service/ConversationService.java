package com.chatbot.saas.service;

import com.chatbot.saas.dto.response.ConversationResponse;
import com.chatbot.saas.entity.*;
import com.chatbot.saas.exception.ConversationNotFoundException;
import com.chatbot.saas.repository.ConversationRepository;
import com.chatbot.saas.repository.FlowStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final FlowStepRepository flowStepRepository;

    @Transactional
    public Conversation createConversation(Customer customer, Business business, ChatbotFlow flow) {
        FlowStep firstStep = flowStepRepository.findFirstByFlowIdOrderByStepOrderAsc(flow.getId())
                .orElse(null);
        Conversation conversation = Conversation.builder()
                .customer(customer)
                .business(business)
                .flow(flow)
                .currentStep(firstStep)
                .status(Conversation.Status.ACTIVE)
                .startedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return conversationRepository.save(conversation);
    }

    @Transactional
    public void updateConversationStep(Conversation conversation, FlowStep step) {
        conversation.setCurrentStep(step);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    @Transactional
    public void completeConversation(Conversation conversation) {
        conversation.setStatus(Conversation.Status.COMPLETED);
        conversation.setCompletedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    @Transactional
    public void abandonConversation(Conversation conversation) {
        conversation.setStatus(Conversation.Status.ABANDONED);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getConversationsByBusiness(Long businessId) {
        return conversationRepository.findAllByBusinessId(businessId)
                .stream()
                .map(ConversationResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversationById(Long id) {
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ConversationNotFoundException(id));
        return ConversationResponse.from(conversation);
    }

    @Transactional(readOnly = true)
    public Optional<Conversation> findActiveConversationForCustomer(Long customerId) {
        return conversationRepository.findByCustomerIdAndStatus(customerId, Conversation.Status.ACTIVE);
    }
}
