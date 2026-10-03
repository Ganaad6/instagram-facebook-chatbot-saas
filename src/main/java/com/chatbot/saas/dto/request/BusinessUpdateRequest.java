package com.chatbot.saas.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BusinessUpdateRequest {
    private String name;

    @Email(message = "Invalid email format")
    private String email;

    private String instagramAccountId;
    private String facebookPageId;

    /** Bot greeting; blank restores the built-in one. */
    @Size(max = 500, message = "Мэндчилгээ 500 тэмдэгтээс ихгүй байна")
    private String welcomeMessage;

    /** Delivery terms in the order confirmation; blank removes it. */
    @Size(max = 500, message = "Хүргэлтийн мэдээлэл 500 тэмдэгтээс ихгүй байна")
    private String deliveryNote;
}
