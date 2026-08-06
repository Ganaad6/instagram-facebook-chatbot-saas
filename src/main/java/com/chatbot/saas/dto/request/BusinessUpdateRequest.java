package com.chatbot.saas.dto.request;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class BusinessUpdateRequest {
    private String name;

    @Email(message = "Invalid email format")
    private String email;

    private String instagramAccountId;
    private String facebookPageId;
}
