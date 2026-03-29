package com.chatbot.saas.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Service
public class ValidationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{10,15}$");

    public boolean validate(String input, String validationType, String validationRegex) {
        if (validationType == null) {
            return true;
        }
        return switch (validationType.toUpperCase()) {
            case "NONE" -> true;
            case "TEXT" -> StringUtils.hasText(input);
            case "PHONE" -> StringUtils.hasText(input) && PHONE_PATTERN.matcher(input.replaceAll("[\\s\\-\\(\\)]", "")).matches();
            case "EMAIL" -> StringUtils.hasText(input) && EMAIL_PATTERN.matcher(input.trim()).matches();
            case "REGEX" -> StringUtils.hasText(input) && validationRegex != null && Pattern.matches(validationRegex, input);
            default -> StringUtils.hasText(input);
        };
    }
}
