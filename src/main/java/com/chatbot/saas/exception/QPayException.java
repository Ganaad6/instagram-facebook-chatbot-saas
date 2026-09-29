package com.chatbot.saas.exception;

/** A QPay API call failed: QPay rejected it, or could not be reached. */
public class QPayException extends RuntimeException {

    private final boolean authenticationFailed;

    public QPayException(String message, boolean authenticationFailed, Throwable cause) {
        super(message, cause);
        this.authenticationFailed = authenticationFailed;
    }

    public QPayException(String message) {
        this(message, false, null);
    }

    /** True when QPay rejected the merchant username/password. */
    public boolean isAuthenticationFailed() {
        return authenticationFailed;
    }
}
