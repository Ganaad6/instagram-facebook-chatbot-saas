package com.chatbot.saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class AnalyticsSummaryResponse {
    private long totalOrders;
    private long pendingOrders;
    private long todayOrders;
    /** Sum of totalAmount over all non-cancelled orders. */
    private BigDecimal totalRevenue;
    private List<TopProductResponse> topProducts;

    @Data
    @Builder
    public static class TopProductResponse {
        private String productName;
        private long orderCount;
        private long totalQuantity;
    }
}
