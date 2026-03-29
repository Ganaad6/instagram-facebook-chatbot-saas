package com.chatbot.saas.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateFlowStepRequest {

    @NotNull(message = "Step order is required")
    private Integer stepOrder;

    @NotBlank(message = "Step key is required")
    private String stepKey;

    @NotBlank(message = "Message template is required")
    private String messageTemplate;

    private String fieldName;
    private String validationType;
    private String validationRegex;
    private Boolean isRequired;
    private String errorMessage;
}
