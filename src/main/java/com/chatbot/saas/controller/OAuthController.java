package com.chatbot.saas.controller;

import com.chatbot.saas.entity.Business;
import com.chatbot.saas.exception.BusinessNotFoundException;
import com.chatbot.saas.exception.MetaConnectException;
import com.chatbot.saas.security.TenantContext;
import com.chatbot.saas.service.OAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/meta")
@RequiredArgsConstructor
@Slf4j
public class OAuthController {

    private final OAuthService oAuthService;
    private final TenantContext tenantContext;

    /**
     * Returns the Meta connect link as JSON rather than redirecting: a browser can't attach the
     * X-API-Key header, so the link is fetched via the API and then opened by the shop owner.
     */
    @GetMapping("/authorize")
    public ResponseEntity<Map<String, Object>> authorize(@RequestParam Long businessId) {
        tenantContext.assertAccess(businessId);
        return ResponseEntity.ok(Map.of(
                "authorizationUrl", oAuthService.generateAuthorizationUrl(businessId),
                "expiresInMinutes", oAuthService.getAuthorizationUrlTtlMinutes()));
    }

    /**
     * Public callback - Meta redirects the business owner's browser here with no way to attach
     * an API key. Trust is instead derived from the signed, expiring `state` (see
     * OAuthStateService), not from a client-suppliable businessId. Responds with a small HTML
     * page since a person is looking at it.
     */
    @GetMapping(value = "/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> callback(@RequestParam(required = false) String code,
                                           @RequestParam(required = false) String state,
                                           @RequestParam(name = "error_description", required = false) String errorDescription) {
        if (code == null || state == null) {
            String reason = errorDescription != null ? errorDescription : "The connection was cancelled.";
            return page(HttpStatus.BAD_REQUEST, "Connection not completed", reason + " Please try the link again.");
        }
        try {
            Business business = oAuthService.handleCallback(code, state);
            String connected = business.getInstagramAccountId() != null
                    ? "Your Facebook Page and Instagram account are connected."
                    : "Your Facebook Page is connected. No Instagram professional account is linked to it, "
                    + "so only Messenger will be answered.";
            return page(HttpStatus.OK, "Connected", connected + " You can close this window.");
        } catch (IllegalArgumentException e) {
            return page(HttpStatus.BAD_REQUEST, "Link expired or invalid",
                    "This connect link is no longer valid. Please request a new one.");
        } catch (MetaConnectException | BusinessNotFoundException e) {
            log.warn("Meta connect failed: {}", e.getMessage());
            return page(HttpStatus.BAD_REQUEST, "Connection failed", e.getMessage());
        }
    }

    private ResponseEntity<String> page(HttpStatus status, String title, String message) {
        String html = "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>" + HtmlUtils.htmlEscape(title) + "</title></head>"
                + "<body style=\"font-family:sans-serif;max-width:32rem;margin:4rem auto;padding:0 1rem\">"
                + "<h1>" + HtmlUtils.htmlEscape(title) + "</h1>"
                + "<p>" + HtmlUtils.htmlEscape(message) + "</p></body></html>";
        return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).body(html);
    }
}
