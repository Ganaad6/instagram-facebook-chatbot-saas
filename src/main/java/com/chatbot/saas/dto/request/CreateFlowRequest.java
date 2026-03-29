package com.chatbot.saas.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateFlowRequest {

    @NotNull(message = "Business ID is required")
    private Long businessId;

    @NotBlank(message = "Flow name is required")
    private String name;
}
