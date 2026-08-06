package com.chatbot.saas.controller;

import com.chatbot.saas.dto.response.CustomerResponse;
import com.chatbot.saas.security.TenantContext;
import com.chatbot.saas.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final TenantContext tenantContext;

    @GetMapping
    public ResponseEntity<List<CustomerResponse>> getCustomers(@RequestParam Long businessId) {
        tenantContext.assertAccess(businessId);
        return ResponseEntity.ok(customerService.getCustomersByBusiness(businessId));
    }
}
