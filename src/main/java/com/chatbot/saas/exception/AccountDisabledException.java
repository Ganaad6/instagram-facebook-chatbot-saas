package com.chatbot.saas.exception;

/** Dashboard login: The account or shop is disabled. */
public class AccountDisabledException extends RuntimeException {
    public AccountDisabledException(String message) {
        super(message);
    }
}
