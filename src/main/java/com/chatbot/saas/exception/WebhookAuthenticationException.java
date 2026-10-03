package com.chatbot.saas.exception;

/** A webhook request that didn't come from Meta: bad signature or wrong verify token. */
public class WebhookAuthenticationException extends RuntimeException {
    public WebhookAuthenticationException(String message) {
        super(message);
    }
}
