package com.chatbot.saas.controller;

import com.chatbot.saas.dto.response.ConversationDataResponse;
import com.chatbot.saas.dto.response.ConversationResponse;
import com.chatbot.saas.dto.response.MessageResponse;
import com.chatbot.saas.security.TenantContext;
import com.chatbot.saas.service.ConversationDataService;
import com.chatbot.saas.service.ConversationService;
import com.chatbot.saas.service.MessageLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationDataService conversationDataService;
    private final MessageLogService messageLogService;
    private final TenantContext tenantContext;

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getConversations(@RequestParam Long businessId) {
        tenantContext.assertAccess(businessId);
        return ResponseEntity.ok(conversationService.getConversationsByBusiness(businessId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> getConversation(@PathVariable Long id) {
        return ResponseEntity.ok(conversationService.getConversationById(id));
    }

    /** Full transcript: the customer's messages and the bot's replies, oldest first. */
    @GetMapping("/{id}/messages")
    public ResponseEntity<List<MessageResponse>> getConversationMessages(@PathVariable Long id) {
        conversationService.getConversationEntityById(id); // tenant access check
        return ResponseEntity.ok(messageLogService.getTranscript(id));
    }

    @GetMapping("/{id}/data")
    public ResponseEntity<List<ConversationDataResponse>> getConversationData(@PathVariable Long id) {
        return ResponseEntity.ok(conversationDataService.getDataByConversation(id));
    }
}
