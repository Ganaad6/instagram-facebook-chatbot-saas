package com.chatbot.saas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** An uploaded image (product photo), served publicly at /media/{id}. */
@Entity
@Table(name = "media_files")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaFile {

    @Id
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Integer sizeBytes;

    @Column(nullable = false, columnDefinition = "BYTEA")
    @ToString.Exclude
    private byte[] data;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
