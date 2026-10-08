package com.chatbot.saas.controller;

import com.chatbot.saas.dto.request.AgentMessageRequest;
import com.chatbot.saas.dto.response.ChatSummaryResponse;
import com.chatbot.saas.dto.response.CustomerResponse;
import com.chatbot.saas.service.ChatListService;
import org.springframework.data.domain.Page;
import com.chatbot.saas.dto.response.MessageResponse;
import com.chatbot.saas.security.TenantContext;
import com.chatbot.saas.service.CustomerDataService;
import com.chatbot.saas.service.CustomerService;
import com.chatbot.saas.service.HandoffService;
import com.chatbot.saas.service.MessageLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Human handoff for shop staff: see who needs attention, read a customer's history, reply to
 * them, and pause or resume the bot for them. Staff can also just reply from the shop's own
 * Meta inbox - the bot pauses automatically when it sees that (see HandoffService).
 */
@RestController
@RequestMapping("/api/businesses/{businessId}")
@RequiredArgsConstructor
public class InboxController {

    private static final int MAX_HISTORY = 500;

    private final HandoffService handoffService;
    private final CustomerService customerService;
    private final MessageLogService messageLogService;
    private final TenantContext tenantContext;
    private final ChatListService chatListService;
    private final CustomerDataService customerDataService;

    /**
     * Every customer conversation, most recently active first (the dashboard's chat list);
     * with waiting=true, only customers waiting for a person, longest-waiting first.
     */
    @GetMapping("/chats")
    public ResponseEntity<Page<ChatSummaryResponse>> chats(@PathVariable Long businessId,
                                                           @RequestParam(defaultValue = "false") boolean waiting,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "30") int size) {
        tenantContext.assertAccess(businessId);
        return ResponseEntity.ok(chatListService.list(businessId, waiting, Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    @GetMapping("/chats/{customerId}")
    public ResponseEntity<ChatSummaryResponse> chat(@PathVariable Long businessId, @PathVariable Long customerId) {
        tenantContext.assertAccess(businessId);
        return ResponseEntity.ok(chatListService.get(businessId, customerId));
    }

    /** Customers waiting for a person (oldest request first), then others the bot is paused for. */
    @GetMapping("/inbox")
    public ResponseEntity<List<CustomerResponse>> inbox(@PathVariable Long businessId) {
        tenantContext.assertAccess(businessId);
        return ResponseEntity.ok(customerService.getInbox(businessId));
    }

    /** The customer's latest messages across all conversations, oldest first. */
    @GetMapping("/customers/{customerId}/messages")
    public ResponseEntity<List<MessageResponse>> history(@PathVariable Long businessId,
                                                         @PathVariable Long customerId,
                                                         @RequestParam(defaultValue = "100") int limit) {
        tenantContext.assertAccess(businessId);
        customerService.assertBelongsTo(businessId, customerId);
        return ResponseEntity.ok(messageLogService.getCustomerHistory(customerId, Math.max(1, Math.min(limit, MAX_HISTORY))));
    }

    /** Send a message to the customer as a staff member; pauses the bot for them. */
    @PostMapping("/customers/{customerId}/messages")
    public ResponseEntity<MessageResponse> sendMessage(@PathVariable Long businessId,
                                                       @PathVariable Long customerId,
                                                       @Valid @RequestBody AgentMessageRequest request) {
        tenantContext.assertAccess(businessId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(handoffService.sendAgentMessage(businessId, customerId, request.getText()));
    }

    /** Silence the bot for this customer (default: the handoff timeout, max 168 hours). */
    @PostMapping("/customers/{customerId}/pause-bot")
    public ResponseEntity<CustomerResponse> pauseBot(@PathVariable Long businessId,
                                                     @PathVariable Long customerId,
                                                     @RequestParam(required = false) Integer hours) {
        tenantContext.assertAccess(businessId);
        return ResponseEntity.ok(handoffService.pauseBot(businessId, customerId, hours));
    }

    /** Hand the customer back to the bot; their next message starts a fresh bot conversation. */
    @PostMapping("/customers/{customerId}/resume-bot")
    public ResponseEntity<CustomerResponse> resumeBot(@PathVariable Long businessId,
                                                      @PathVariable Long customerId) {
        tenantContext.assertAccess(businessId);
        return ResponseEntity.ok(handoffService.resumeBot(businessId, customerId));
    }

    /**
     * Erases a customer's personal data (e.g. they asked the shop to): chat history deleted,
     * contact details removed from their orders. Owners only; cannot be undone.
     */
    @DeleteMapping("/customers/{customerId}")
    public ResponseEntity<Void> eraseCustomer(@PathVariable Long businessId, @PathVariable Long customerId) {
        tenantContext.assertOwner(businessId);
        customerDataService.erase(businessId, customerId);
        return ResponseEntity.noContent().build();
    }
}
