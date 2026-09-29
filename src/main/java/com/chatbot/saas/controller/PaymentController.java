package com.chatbot.saas.controller;

import com.chatbot.saas.dto.request.QPayConnectRequest;
import com.chatbot.saas.dto.response.OrderResponse;
import com.chatbot.saas.entity.Business;
import com.chatbot.saas.security.TenantContext;
import com.chatbot.saas.service.BusinessService;
import com.chatbot.saas.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/businesses/{businessId}")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final BusinessService businessService;
    private final TenantContext tenantContext;

    /** Connects (or replaces) the shop's QPay merchant account; the credentials are checked with QPay first. */
    @PutMapping("/payments/qpay")
    public ResponseEntity<Map<String, Object>> connectQPay(@PathVariable Long businessId,
                                                           @Valid @RequestBody QPayConnectRequest request) {
        tenantContext.assertAccess(businessId);
        Business business = businessService.findBusinessById(businessId);
        paymentService.connectQPay(business, request.getUsername().trim(), request.getPassword(),
                request.getInvoiceCode().trim());
        return ResponseEntity.ok(Map.of("qpayConnected", true));
    }

    @DeleteMapping("/payments/qpay")
    public ResponseEntity<Map<String, Object>> disconnectQPay(@PathVariable Long businessId) {
        tenantContext.assertAccess(businessId);
        paymentService.disconnectQPay(businessService.findBusinessById(businessId));
        return ResponseEntity.ok(Map.of("qpayConnected", false));
    }

    /** Asks QPay for the order's payment status now instead of waiting for the callback. */
    @PostMapping("/orders/{orderId}/payment/check")
    public ResponseEntity<OrderResponse> checkPayment(@PathVariable Long businessId, @PathVariable Long orderId) {
        tenantContext.assertAccess(businessId);
        return ResponseEntity.ok(paymentService.checkPayment(businessId, orderId));
    }
}
