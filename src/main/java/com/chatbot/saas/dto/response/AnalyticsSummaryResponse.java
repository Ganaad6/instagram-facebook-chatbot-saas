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
    /** Sum of orders paid through QPay. */
    private BigDecimal paidRevenue;
    /** Open orders whose QPay invoice hasn't been paid yet. */
    private long awaitingPaymentOrders;
    private List<TopProductResponse> topProducts;

    @Data
    @Builder
    public static class TopProductResponse {
        private String productName;
        private long orderCount;
        private long totalQuantity;
    }
}
