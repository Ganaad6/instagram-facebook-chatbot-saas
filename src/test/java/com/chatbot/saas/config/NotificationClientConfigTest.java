package com.chatbot.saas.config;

import org.junit.jupiter.api.Test;

import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;

class NotificationClientConfigTest {

    /** Even if a saved URL's DNS later points inside the network, the client won't connect. */
    @Test
    void refusesToConnectToPrivateAddresses() {
        var client = new NotificationClientConfig().notificationWebClient();

        Exception e = assertThrows(Exception.class, () -> client.post().uri("http://localhost:1/hook")
                .bodyValue("{}").retrieve().toBodilessEntity().block());

        Throwable cause = e;
        while (cause != null && !(cause instanceof UnknownHostException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause, "expected the resolver to refuse, got " + e);
    }
}
