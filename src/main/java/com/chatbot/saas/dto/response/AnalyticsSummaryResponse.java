package com.chatbot.saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AnalyticsSummaryResponse {
    private long totalOrders;
    private long pendingOrders;
    private long todayOrders;
    private List<TopProductResponse> topProducts;

    @Data
    @Builder
    public static class TopProductResponse {
        private String productName;
        private long orderCount;
    }
}
