package com.chatbot.saas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A completed Meta data-deletion request; who asked is deliberately not stored. */
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

    /** How many shops' Facebook connections were removed. */
    @Column(name = "connections_removed", nullable = false)
    private int connectionsRemoved;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;
}
