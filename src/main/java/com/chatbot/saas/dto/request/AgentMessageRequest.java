package com.chatbot.saas.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentMessageRequest {
    @NotBlank(message = "text is required")
    @Size(max = 2000, message = "text must be at most 2000 characters")
    private String text;
}
