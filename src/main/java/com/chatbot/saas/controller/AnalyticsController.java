package com.chatbot.saas.controller;

import com.chatbot.saas.dto.response.AnalyticsSummaryResponse;
import com.chatbot.saas.dto.response.DailyOrderCountResponse;
import com.chatbot.saas.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/businesses/{businessId}/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final OrderService orderService;

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryResponse> getSummary(@PathVariable Long businessId) {
        return ResponseEntity.ok(orderService.getSummary(businessId));
    }

    @GetMapping("/orders-by-day")
    public ResponseEntity<List<DailyOrderCountResponse>> getOrdersByDay(
            @PathVariable Long businessId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDateTime fromDt = from != null ? from.atStartOfDay() : LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime toDt = to != null ? to.plusDays(1).atStartOfDay() : LocalDateTime.now();
        return ResponseEntity.ok(orderService.getOrdersByDay(businessId, fromDt, toDt));
    }
}
