package com.chatbot.saas.controller;

import com.chatbot.saas.dto.response.ConversationDataResponse;
import com.chatbot.saas.dto.response.ConversationResponse;
import com.chatbot.saas.service.ConversationDataService;
import com.chatbot.saas.service.ConversationService;
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

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getConversations(@RequestParam Long businessId) {
        return ResponseEntity.ok(conversationService.getConversationsByBusiness(businessId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> getConversation(@PathVariable Long id) {
        return ResponseEntity.ok(conversationService.getConversationById(id));
    }

    @GetMapping("/{id}/data")
    public ResponseEntity<List<ConversationDataResponse>> getConversationData(@PathVariable Long id) {
        return ResponseEntity.ok(conversationDataService.getDataByConversation(id));
    }
}
