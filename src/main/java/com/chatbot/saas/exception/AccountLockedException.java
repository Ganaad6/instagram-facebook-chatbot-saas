package com.chatbot.saas.exception;

/** Dashboard login: Too many failed attempts. */
public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String message) {
        super(message);
    }
}
