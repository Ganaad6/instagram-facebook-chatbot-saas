package com.chatbot.saas.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** The shop's QPay merchant credentials, as issued by QPay with the merchant account. */
@Data
public class QPayConnectRequest {
    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    private String invoiceCode;
}
