package com.chatbot.saas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A data-deletion request Meta forwarded for a Facebook user; its code opens the public status page. */
@Entity
@Table(name = "data_deletion_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataDeletionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "confirmation_code", nullable = false, unique = true)
    private String confirmationCode;

    @Column(name = "shops_affected", nullable = false)
    private int shopsAffected;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
