package com.chatbot.saas.dto.response;

import com.chatbot.saas.entity.ChatbotFlow;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class FlowResponse {
    private Long id;
    private Long businessId;
    private String name;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private List<FlowStepResponse> steps;

    public static FlowResponse from(ChatbotFlow flow) {
        List<FlowStepResponse> stepResponses = flow.getSteps() != null
                ? flow.getSteps().stream().map(FlowStepResponse::from).collect(Collectors.toList())
                : List.of();

        return FlowResponse.builder()
                .id(flow.getId())
                .businessId(flow.getBusiness().getId())
                .name(flow.getName())
                .isActive(flow.getIsActive())
                .createdAt(flow.getCreatedAt())
                .steps(stepResponses)
                .build();
    }
}
