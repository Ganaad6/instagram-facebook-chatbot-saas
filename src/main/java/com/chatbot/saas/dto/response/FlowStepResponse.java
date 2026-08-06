package com.chatbot.saas.dto.response;

import com.chatbot.saas.entity.FlowStep;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FlowStepResponse {
    private Long id;
    private Integer stepOrder;
    private String stepKey;
    private String messageTemplate;
    private String fieldName;
    private String validationType;
    private String validationRegex;
    private Boolean isRequired;
    private String errorMessage;
    private Long nextStepId;

    public static FlowStepResponse from(FlowStep step) {
        return FlowStepResponse.builder()
                .id(step.getId())
                .stepOrder(step.getStepOrder())
                .stepKey(step.getStepKey())
                .messageTemplate(step.getMessageTemplate())
                .fieldName(step.getFieldName())
                .validationType(step.getValidationType() != null ? step.getValidationType().name() : null)
                .validationRegex(step.getValidationRegex())
                .isRequired(step.getIsRequired())
                .errorMessage(step.getErrorMessage())
                .nextStepId(step.getNextStep() != null ? step.getNextStep().getId() : null)
                .build();
    }
}
