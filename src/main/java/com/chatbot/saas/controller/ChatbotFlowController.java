package com.chatbot.saas.controller;

import com.chatbot.saas.dto.request.CreateFlowRequest;
import com.chatbot.saas.dto.request.CreateFlowStepRequest;
import com.chatbot.saas.dto.request.UpdateFlowRequest;
import com.chatbot.saas.dto.request.UpdateFlowStepRequest;
import com.chatbot.saas.dto.response.FlowResponse;
import com.chatbot.saas.dto.response.FlowStepResponse;
import com.chatbot.saas.service.ChatbotFlowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flows")
@RequiredArgsConstructor
public class ChatbotFlowController {

    private final ChatbotFlowService chatbotFlowService;

    @PostMapping
    public ResponseEntity<FlowResponse> createFlow(@Valid @RequestBody CreateFlowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chatbotFlowService.createFlow(request));
    }

    @GetMapping
    public ResponseEntity<List<FlowResponse>> getFlowsByBusiness(@RequestParam Long businessId) {
        return ResponseEntity.ok(chatbotFlowService.getFlowsByBusiness(businessId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FlowResponse> updateFlow(@PathVariable Long id,
                                                   @RequestBody UpdateFlowRequest request) {
        return ResponseEntity.ok(chatbotFlowService.updateFlow(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlow(@PathVariable Long id) {
        chatbotFlowService.deleteFlow(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/steps")
    public ResponseEntity<FlowStepResponse> addStep(@PathVariable Long id,
                                                     @Valid @RequestBody CreateFlowStepRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chatbotFlowService.addStep(id, request));
    }

    @PutMapping("/steps/{id}")
    public ResponseEntity<FlowStepResponse> updateStep(@PathVariable Long id,
                                                        @RequestBody UpdateFlowStepRequest request) {
        return ResponseEntity.ok(chatbotFlowService.updateStep(id, request));
    }

    @DeleteMapping("/steps/{id}")
    public ResponseEntity<Void> deleteStep(@PathVariable Long id) {
        chatbotFlowService.deleteStep(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<FlowResponse> activateFlow(@PathVariable Long id) {
        return ResponseEntity.ok(chatbotFlowService.activateFlow(id));
    }
}
