package com.chatbot.saas.exception;

public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(Long id) {
        super("Customer not found with id: " + id);
    }
    public CustomerNotFoundException(String message) {
        super(message);
    }
}
