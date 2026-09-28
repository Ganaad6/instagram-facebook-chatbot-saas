package com.chatbot.saas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "customers", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"business_id", "instagram_user_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(name = "instagram_user_id")
    private String instagramUserId;

    @Column(name = "facebook_user_id")
    private String facebookUserId;

    @Column(name = "first_interaction_at", nullable = false)
    private LocalDateTime firstInteractionAt;

    @Column(name = "last_interaction_at", nullable = false)
    private LocalDateTime lastInteractionAt;

    /** While in the future, a staff member has the conversation and the bot stays silent. */
    @Column(name = "bot_paused_until")
    private LocalDateTime botPausedUntil;

    /** The customer asked for a person and hasn't had a staff reply yet. */
    @Column(name = "handoff_requested_at")
    private LocalDateTime handoffRequestedAt;

    public boolean isBotPaused(LocalDateTime now) {
        return botPausedUntil != null && botPausedUntil.isAfter(now);
    }

    /** "FACEBOOK" or "INSTAGRAM", from which platform ID this customer has. */
    public String getPlatform() {
        return facebookUserId != null ? "FACEBOOK" : "INSTAGRAM";
    }

    /** The ID Meta uses for this person on the given platform - the recipient ID for replies. */
    public String getPlatformUserId(String platform) {
        return "FACEBOOK".equalsIgnoreCase(platform) ? facebookUserId : instagramUserId;
    }

    @PrePersist
    protected void onCreate() {
        if (firstInteractionAt == null) {
            firstInteractionAt = LocalDateTime.now();
        }
        lastInteractionAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastInteractionAt = LocalDateTime.now();
    }
}
