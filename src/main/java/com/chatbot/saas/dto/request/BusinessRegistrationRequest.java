package com.chatbot.saas.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BusinessRegistrationRequest {

    @NotBlank(message = "Business name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String instagramAccountId;
    private String facebookPageId;
}
