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

    @Column(name = "instagram_user_id", nullable = false)
    private String instagramUserId;

    @Column(name = "facebook_user_id")
    private String facebookUserId;

    @Column(name = "first_interaction_at", nullable = false)
    private LocalDateTime firstInteractionAt;

    @Column(name = "last_interaction_at", nullable = false)
    private LocalDateTime lastInteractionAt;

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
