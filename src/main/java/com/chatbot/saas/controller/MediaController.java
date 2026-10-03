package com.chatbot.saas.controller;

import com.chatbot.saas.entity.MediaFile;
import com.chatbot.saas.security.TenantContext;
import com.chatbot.saas.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;
    private final TenantContext tenantContext;

    /** Uploads a product photo (multipart field "file"); use the returned id as imageFileId. */
    @PostMapping("/api/businesses/{businessId}/media")
    public ResponseEntity<MediaService.Upload> upload(@PathVariable Long businessId,
                                                      @RequestParam("file") MultipartFile file) throws IOException {
        tenantContext.assertAccess(businessId);
        return ResponseEntity.status(HttpStatus.CREATED).body(mediaService.store(businessId, file.getBytes()));
    }

    /**
     * Public: Meta downloads product photos from here, and the dashboard shows them. Ids are
     * random and a photo never changes once uploaded, so it can be cached forever.
     */
    @GetMapping("/media/{id}")
    public ResponseEntity<byte[]> get(@PathVariable UUID id) {
        return mediaService.find(id)
                .map(file -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(file.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .header("X-Content-Type-Options", "nosniff")
                        .header("Content-Security-Policy", "default-src 'none'; sandbox")
                        .body(file.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
