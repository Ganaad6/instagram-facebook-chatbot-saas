package com.chatbot.saas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A person who signs in to a shop's dashboard. */
@Entity
@Table(name = "staff_users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffUser {

    /** OWNER also manages settings, connections and staff; STAFF handles orders, catalog and chats. */
    public enum Role {
        OWNER, STAFF
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(nullable = false)
    private String email;

    private String name;

    /** BCrypt; null until the invite is accepted. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "session_version", nullable = false)
    @Builder.Default
    private int sessionVersion = 0;

    @Column(name = "failed_logins", nullable = false)
    @Builder.Default
    private int failedLogins = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Signs out every session of this user. */
    public void invalidateSessions() {
        sessionVersion++;
    }
}
