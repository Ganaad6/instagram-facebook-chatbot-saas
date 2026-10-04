package com.chatbot.saas.controller;

import com.chatbot.saas.dto.response.AdminUserResponse;
import com.chatbot.saas.service.StaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Operator-only (ROLE_ADMIN): every dashboard login across all shops, for the /admin page. */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final StaffService staffService;

    @GetMapping
    public ResponseEntity<List<AdminUserResponse>> listUsers() {
        return ResponseEntity.ok(staffService.listAll());
    }
}
