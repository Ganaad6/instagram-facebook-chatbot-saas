package com.chatbot.saas.service;

import com.chatbot.saas.dto.request.CreateFlowRequest;
import com.chatbot.saas.dto.request.CreateFlowStepRequest;
import com.chatbot.saas.dto.request.UpdateFlowRequest;
import com.chatbot.saas.dto.request.UpdateFlowStepRequest;
import com.chatbot.saas.dto.response.FlowResponse;
import com.chatbot.saas.dto.response.FlowStepResponse;
import com.chatbot.saas.entity.Business;
import com.chatbot.saas.entity.ChatbotFlow;
import com.chatbot.saas.entity.FlowStep;
import com.chatbot.saas.exception.BusinessNotFoundException;
import com.chatbot.saas.exception.FlowNotFoundException;
import com.chatbot.saas.repository.BusinessRepository;
import com.chatbot.saas.repository.ChatbotFlowRepository;
import com.chatbot.saas.repository.FlowStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotFlowService {

    private final ChatbotFlowRepository chatbotFlowRepository;
    private final FlowStepRepository flowStepRepository;
    private final BusinessRepository businessRepository;

    @Transactional
    public FlowResponse createFlow(CreateFlowRequest request) {
        Business business = businessRepository.findById(request.getBusinessId())
                .orElseThrow(() -> new BusinessNotFoundException(request.getBusinessId()));
        ChatbotFlow flow = ChatbotFlow.builder()
                .business(business)
                .name(request.getName())
                .isActive(false)
                .build();
        return FlowResponse.from(chatbotFlowRepository.save(flow));
    }

    @Transactional(readOnly = true)
    public List<FlowResponse> getFlowsByBusiness(Long businessId) {
        return chatbotFlowRepository.findAllByBusinessId(businessId)
                .stream()
                .map(FlowResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public FlowResponse updateFlow(Long id, UpdateFlowRequest request) {
        ChatbotFlow flow = findFlowById(id);
        if (request.getName() != null) flow.setName(request.getName());
        return FlowResponse.from(chatbotFlowRepository.save(flow));
    }

    @Transactional
    public void deleteFlow(Long id) {
        ChatbotFlow flow = findFlowById(id);
        chatbotFlowRepository.delete(flow);
    }

    @Transactional
    public FlowStepResponse addStep(Long flowId, CreateFlowStepRequest request) {
        ChatbotFlow flow = findFlowById(flowId);
        FlowStep.ValidationType validationType = null;
        if (request.getValidationType() != null) {
            try {
                validationType = FlowStep.ValidationType.valueOf(request.getValidationType().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown validation type: {}", request.getValidationType());
            }
        }
        FlowStep step = FlowStep.builder()
                .flow(flow)
                .stepOrder(request.getStepOrder())
                .stepKey(request.getStepKey())
                .messageTemplate(request.getMessageTemplate())
                .fieldName(request.getFieldName())
                .validationType(validationType)
                .validationRegex(request.getValidationRegex())
                .isRequired(request.getIsRequired() != null ? request.getIsRequired() : true)
                .errorMessage(request.getErrorMessage())
                .build();
        return FlowStepResponse.from(flowStepRepository.save(step));
    }

    @Transactional
    public FlowStepResponse updateStep(Long stepId, UpdateFlowStepRequest request) {
        FlowStep step = flowStepRepository.findById(stepId)
                .orElseThrow(() -> new FlowNotFoundException("Step not found with id: " + stepId));
        if (request.getStepOrder() != null) step.setStepOrder(request.getStepOrder());
        if (request.getStepKey() != null) step.setStepKey(request.getStepKey());
        if (request.getMessageTemplate() != null) step.setMessageTemplate(request.getMessageTemplate());
        if (request.getFieldName() != null) step.setFieldName(request.getFieldName());
        if (request.getValidationType() != null) {
            try {
                step.setValidationType(FlowStep.ValidationType.valueOf(request.getValidationType().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown validation type: {}", request.getValidationType());
            }
        }
        if (request.getValidationRegex() != null) step.setValidationRegex(request.getValidationRegex());
        if (request.getIsRequired() != null) step.setIsRequired(request.getIsRequired());
        if (request.getErrorMessage() != null) step.setErrorMessage(request.getErrorMessage());
        return FlowStepResponse.from(flowStepRepository.save(step));
    }

    @Transactional
    public void deleteStep(Long stepId) {
        FlowStep step = flowStepRepository.findById(stepId)
                .orElseThrow(() -> new FlowNotFoundException("Step not found with id: " + stepId));
        flowStepRepository.delete(step);
    }

    @Transactional
    public FlowResponse activateFlow(Long flowId) {
        ChatbotFlow flow = findFlowById(flowId);
        // Deactivate all other flows for this business
        List<ChatbotFlow> allFlows = chatbotFlowRepository.findAllByBusinessId(flow.getBusiness().getId());
        for (ChatbotFlow f : allFlows) {
            if (!f.getId().equals(flowId) && Boolean.TRUE.equals(f.getIsActive())) {
                f.setIsActive(false);
                chatbotFlowRepository.save(f);
            }
        }
        flow.setIsActive(true);
        return FlowResponse.from(chatbotFlowRepository.save(flow));
    }

    private ChatbotFlow findFlowById(Long id) {
        return chatbotFlowRepository.findById(id)
                .orElseThrow(() -> new FlowNotFoundException(id));
    }
}
