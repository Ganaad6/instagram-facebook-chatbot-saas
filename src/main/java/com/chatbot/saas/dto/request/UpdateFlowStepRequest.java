package com.chatbot.saas.dto.request;

import lombok.Data;

@Data
public class UpdateFlowStepRequest {
    private Integer stepOrder;
    private String stepKey;
    private String messageTemplate;
    private String fieldName;
    private String validationType;
    private String validationRegex;
    private Boolean isRequired;
    private String errorMessage;
}
