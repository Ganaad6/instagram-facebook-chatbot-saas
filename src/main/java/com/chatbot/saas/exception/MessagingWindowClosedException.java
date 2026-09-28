package com.chatbot.saas.exception;

/** Meta's messaging policy doesn't allow sending this customer a message right now. */
public class MessagingWindowClosedException extends RuntimeException {
    public MessagingWindowClosedException(String message) {
        super(message);
    }
}
