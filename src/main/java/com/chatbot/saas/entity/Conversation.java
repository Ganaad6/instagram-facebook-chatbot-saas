package com.chatbot.saas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    public enum Status {
        ACTIVE, COMPLETED, ABANDONED
    }

    public enum State {
        IDLE,
        AWAITING_CATEGORY,
        AWAITING_PRODUCT,
        AWAITING_CONFIRMATION,
        COLLECT_NAME,
        COLLECT_PHONE,
        COLLECT_ADDRESS,
        ORDER_SAVED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flow_id")
    private ChatbotFlow flow;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_step_id")
    private FlowStep currentStep;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private State state = State.IDLE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_category_id")
    private Category selectedCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_product_id")
    private Product selectedProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "collected_name")
    private String collectedName;

    @Column(name = "collected_phone")
    private String collectedPhone;

    @Column(name = "collected_address", columnDefinition = "TEXT")
    private String collectedAddress;

    @Column(name = "platform", length = 20)
    private String platform;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
