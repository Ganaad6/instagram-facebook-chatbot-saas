package com.chatbot.saas.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "flow_steps")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowStep {

    public enum ValidationType {
        TEXT, PHONE, EMAIL, NONE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flow_id", nullable = false)
    private ChatbotFlow flow;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "step_key", nullable = false, length = 100)
    private String stepKey;

    @Column(name = "message_template", nullable = false, columnDefinition = "TEXT")
    private String messageTemplate;

    @Column(name = "field_name", length = 100)
    private String fieldName;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_type", length = 50)
    private ValidationType validationType;

    @Column(name = "validation_regex", length = 500)
    private String validationRegex;

    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private Boolean isRequired = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "next_step_id")
    private FlowStep nextStep;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
