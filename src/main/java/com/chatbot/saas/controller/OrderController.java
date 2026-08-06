package com.chatbot.saas.controller;

import com.chatbot.saas.dto.request.UpdateOrderStatusRequest;
import com.chatbot.saas.dto.response.OrderResponse;
import com.chatbot.saas.entity.Order;
import com.chatbot.saas.security.TenantContext;
import com.chatbot.saas.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/businesses/{businessId}/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final TenantContext tenantContext;

    @GetMapping
    public ResponseEntity<Page<OrderResponse>> getOrders(
            @PathVariable Long businessId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        tenantContext.assertAccess(businessId);
        return ResponseEntity.ok(orderService.getOrdersByBusiness(businessId, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable Long businessId,
            @PathVariable Long id) {
        tenantContext.assertAccess(businessId);
        return ResponseEntity.ok(orderService.getOrderById(businessId, id));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable Long businessId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        tenantContext.assertAccess(businessId);
        return ResponseEntity.ok(orderService.updateOrderStatus(businessId, id, request.getStatus()));
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportOrders(
            @PathVariable Long businessId,
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        tenantContext.assertAccess(businessId);

        LocalDateTime fromDt = from != null ? from.atStartOfDay() : LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime toDt = to != null ? to.plusDays(1).atStartOfDay() : LocalDateTime.now();

        List<Order> orders = orderService.getOrdersForExport(businessId, fromDt, toDt);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("id,product,customerName,phone,address,status,platform,createdAt");
        for (Order o : orders) {
            pw.printf("%d,\"%s\",\"%s\",\"%s\",\"%s\",%s,%s,%s%n",
                    o.getId(),
                    escape(o.getProduct().getName()),
                    escape(o.getCustomerName()),
                    escape(o.getPhone()),
                    escape(o.getAddress()),
                    o.getStatus().name(),
                    o.getPlatform().name(),
                    o.getCreatedAt().toString());
        }

        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"orders.csv\"")
                .body(sw.toString());
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\"\"");
    }
}
