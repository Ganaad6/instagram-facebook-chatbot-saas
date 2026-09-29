package com.chatbot.saas.controller;

import com.chatbot.saas.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * QPay calls this when an invoice is paid. It is public, so nothing in the request is trusted:
 * it only prompts PaymentService to ask QPay about the order. Always answers 200 so QPay
 * doesn't keep retrying a callback we chose to ignore.
 */
@RestController
@RequestMapping("/webhook/qpay")
@RequiredArgsConstructor
public class QPayCallbackController {

    private final PaymentService paymentService;

    @RequestMapping(value = "/{orderId}", method = {RequestMethod.GET, RequestMethod.POST},
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> callback(@PathVariable Long orderId,
                                           @RequestParam(required = false) String token) {
        paymentService.handleCallback(orderId, token);
        return ResponseEntity.ok("SUCCESS");
    }
}
