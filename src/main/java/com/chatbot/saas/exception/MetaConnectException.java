package com.chatbot.saas.exception;

/**
 * Connecting a business's Facebook Page / Instagram account failed - either Meta rejected a
 * call, or the granted Pages don't unambiguously map to this business.
 */
public class MetaConnectException extends RuntimeException {
    public MetaConnectException(String message) {
        super(message);
    }

    public MetaConnectException(String message, Throwable cause) {
        super(message, cause);
    }
}
