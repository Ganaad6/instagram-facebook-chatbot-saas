package com.chatbot.saas.controller;

import com.chatbot.saas.dto.request.AuthRequests;
import com.chatbot.saas.dto.response.BusinessResponse;
import com.chatbot.saas.dto.response.StaffUserResponse;
import com.chatbot.saas.entity.StaffUser;
import com.chatbot.saas.security.StaffPrincipal;
import com.chatbot.saas.security.StaffSessionFilter;
import com.chatbot.saas.security.TenantContext;
import com.chatbot.saas.service.StaffAuthService;
import com.chatbot.saas.exception.TenantAccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Dashboard sign-in. Sessions are cookie based (Spring Session, stored in Postgres); every
 * state-changing request from the dashboard must carry the X-XSRF-TOKEN header, which the
 * browser reads from the XSRF-TOKEN cookie (fetch GET /api/auth/csrf first to get one).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final StaffAuthService staffAuthService;
    private final TenantContext tenantContext;

    public record Me(StaffUserResponse user, BusinessResponse business) {
        static Me of(StaffUser user) {
            return new Me(StaffUserResponse.from(user), BusinessResponse.from(user.getBusiness()));
        }
    }

    /** Issues the XSRF-TOKEN cookie (set by CsrfCookieFilter on every response). */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<Me> login(@Valid @RequestBody AuthRequests.Login request, HttpServletRequest http) {
        StaffUser user = staffAuthService.authenticate(request.email(), request.password());
        startSession(http, user);
        return ResponseEntity.ok(Me.of(user));
    }

    @PostMapping("/signup")
    public ResponseEntity<Me> signup(@Valid @RequestBody AuthRequests.Signup request, HttpServletRequest http) {
        StaffUser owner = staffAuthService.signup(request.businessName(), request.name(), request.email(), request.password());
        startSession(http, owner);
        return ResponseEntity.status(HttpStatus.CREATED).body(Me.of(staffAuthService.getUser(owner.getId())));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<Me> me() {
        return ResponseEntity.ok(Me.of(staffAuthService.getUser(staff().userId())));
    }

    /** Signs out the user's other sessions; this one stays signed in. */
    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody AuthRequests.ChangePassword request,
                                               HttpServletRequest http) {
        StaffUser user = staffAuthService.changePassword(staff().userId(), request.currentPassword(), request.newPassword());
        startSession(http, user);
        return ResponseEntity.noContent().build();
    }

    /** Public: what an invite / password-reset link is for, so the page can greet the person. */
    @GetMapping("/links/{token}")
    public ResponseEntity<StaffAuthService.LinkInfo> describeLink(@PathVariable String token) {
        return ResponseEntity.ok(staffAuthService.describeLink(token));
    }

    /** Public: sets the password from an invite / reset link and signs the person in. */
    @PostMapping("/links/accept")
    public ResponseEntity<Me> acceptLink(@Valid @RequestBody AuthRequests.AcceptLink request, HttpServletRequest http) {
        StaffUser user = staffAuthService.acceptLink(request.token(), request.name(), request.password());
        startSession(http, user);
        return ResponseEntity.ok(Me.of(staffAuthService.getUser(user.getId())));
    }

    private StaffPrincipal staff() {
        return tenantContext.currentStaff().orElseThrow(TenantAccessDeniedException::new);
    }

    /** A fresh session id on every sign-in, so a session id planted beforehand is useless. */
    private static void startSession(HttpServletRequest http, StaffUser user) {
        HttpSession old = http.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = http.getSession(true);
        session.setAttribute(StaffSessionFilter.USER_ID_ATTRIBUTE, user.getId());
        session.setAttribute(StaffSessionFilter.SESSION_VERSION_ATTRIBUTE, user.getSessionVersion());
    }
}
