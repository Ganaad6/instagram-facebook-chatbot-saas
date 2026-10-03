package com.chatbot.saas.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderControllerCsvTest {

    @Test
    void formulaPrefixesAreNeutralized() {
        assertEquals("'=HYPERLINK(\"\"http://evil\"\")", OrderController.escape("=HYPERLINK(\"http://evil\")"));
        assertEquals("'+1", OrderController.escape("+1"));
        assertEquals("'@SUM(A1)", OrderController.escape("@SUM(A1)"));
    }

    @Test
    void ordinaryValuesAreOnlyQuoteEscaped() {
        assertEquals("Бат \"\"Болд\"\"", OrderController.escape("Бат \"Болд\""));
        assertEquals("", OrderController.escape(null));
    }
}
