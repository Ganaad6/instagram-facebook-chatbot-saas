package com.chatbot.saas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class MetaApiConfig {

    @Value("${meta.graph.api.url}")
    private String graphApiUrl;

    @Bean
    public WebClient metaWebClient() {
        return WebClient.builder()
                .baseUrl(graphApiUrl)
                .build();
    }
}
