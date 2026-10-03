package com.chatbot.saas.exception;

public class BusinessNotFoundException extends RuntimeException {
    public BusinessNotFoundException(Long id) {
        super("Business not found with id: " + id);
    }
    public BusinessNotFoundException(String message) {
        super(message);
    }
}
