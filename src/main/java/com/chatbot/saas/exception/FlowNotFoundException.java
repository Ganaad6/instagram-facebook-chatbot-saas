package com.chatbot.saas.exception;

public class FlowNotFoundException extends RuntimeException {
    public FlowNotFoundException(Long id) {
        super("Flow not found with id: " + id);
    }
    public FlowNotFoundException(String message) {
        super(message);
    }
}
