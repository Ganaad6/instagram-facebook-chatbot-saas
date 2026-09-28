package com.chatbot.saas.exception;

/** Meta rejected or never answered a send request. */
public class MetaSendFailedException extends RuntimeException {
    public MetaSendFailedException(String message) {
        super(message);
    }
}
