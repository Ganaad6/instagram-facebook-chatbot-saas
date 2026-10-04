package com.chatbot.saas.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the dashboard (a single-page app built from frontend/ into static/) for each of its
 * client-side routes, so reloading or opening a link like /orders/12 works. index.html is never
 * cached, so a deploy is picked up at once; its hashed /assets/* are cached forever (WebConfig).
 */
@Controller
public class SpaController {

    private static final Resource INDEX = new ClassPathResource("static/index.html");

    @GetMapping({"/", "/login", "/signup", "/invite/{token}", "/orders", "/orders/{id}",
            "/products", "/chats", "/chats/{id}", "/settings", "/settings/{tab}", "/admin"})
    public ResponseEntity<Resource> index() {
        if (!INDEX.exists()) {
            // Backend-only build (e.g. mvn -Dskip.frontend)
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.TEXT_HTML)
                .body(INDEX);
    }
}
