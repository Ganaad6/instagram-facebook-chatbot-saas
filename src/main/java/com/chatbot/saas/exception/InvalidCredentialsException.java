package com.chatbot.saas.exception;

/** Dashboard login: Wrong email or password, or an unusable login link. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
