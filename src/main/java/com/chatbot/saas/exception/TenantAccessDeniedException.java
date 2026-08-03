package com.chatbot.saas.exception;

public class TenantAccessDeniedException extends RuntimeException {
    public TenantAccessDeniedException() {
        super("You do not have access to this resource");
    }
}
