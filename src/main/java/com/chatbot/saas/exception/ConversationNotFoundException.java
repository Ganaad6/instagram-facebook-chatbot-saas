package com.chatbot.saas.exception;

public class ConversationNotFoundException extends RuntimeException {
    public ConversationNotFoundException(Long id) {
        super("Conversation not found with id: " + id);
    }
    public ConversationNotFoundException(String message) {
        super(message);
    }
}
