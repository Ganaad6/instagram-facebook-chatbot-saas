package com.chatbot.saas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "businesses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Business {

    public enum Status {
        ACTIVE, INACTIVE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "instagram_account_id", unique = true)
    private String instagramAccountId;

    @Column(name = "facebook_page_id", unique = true)
    private String facebookPageId;

    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "webhook_verify_token")
    private String webhookVerifyToken;

    @Column(name = "api_key_hash", unique = true)
    private String apiKeyHash;

    @Column(name = "api_key_created_at")
    private LocalDateTime apiKeyCreatedAt;

    @Column(name = "notification_webhook_url", columnDefinition = "TEXT")
    private String notificationWebhookUrl;

    /** QPay merchant login; payments are made to the shop's own QPay account. */
    @Column(name = "qpay_username")
    private String qpayUsername;

    /** QPay merchant password, AES-GCM encrypted. */
    @Column(name = "qpay_password", columnDefinition = "TEXT")
    private String qpayPassword;

    @Column(name = "qpay_invoice_code")
    private String qpayInvoiceCode;

    /** Greeting on the first menu of a conversation; null = the built-in one. */
    @Column(name = "welcome_message", columnDefinition = "TEXT")
    private String welcomeMessage;

    /** Delivery terms added to the order confirmation; null = none. */
    @Column(name = "delivery_note", columnDefinition = "TEXT")
    private String deliveryNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.ACTIVE;

    public boolean isQpayConnected() {
        return qpayUsername != null && qpayPassword != null && qpayInvoiceCode != null;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = Status.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
