package com.chatbot.saas.service;

import com.chatbot.saas.repository.MediaFileRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Product photos: upload, public serving (Meta fetches them), and use on products. */
@SpringBootTest
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.EMBEDDED)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaIntegrationTest {

    /** A real 1x1 PNG. */
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private MediaService mediaService;
    @Autowired private MediaFileRepository mediaFileRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private long businessId;
    private String apiKey;
    private long categoryId;

    @BeforeEach
    void shop() throws Exception {
        JsonNode shop = register();
        businessId = shop.get("id").asLong();
        apiKey = shop.get("apiKey").asString();
        categoryId = json(mockMvc.perform(post("/api/businesses/" + businessId + "/categories")
                .header("X-API-Key", apiKey).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Цэцэг\"}")).andReturn().getResponse()).get("id").asLong();
    }

    private JsonNode register() throws Exception {
        return json(mockMvc.perform(post("/api/businesses/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Shop", "email", "m" + System.nanoTime() + "@example.com"))))
                .andReturn().getResponse());
    }

    private JsonNode json(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private MockHttpServletResponse upload(String key, long business, byte[] bytes, String claimedType) throws Exception {
        return mockMvc.perform(multipart("/api/businesses/" + business + "/media")
                        .file(new MockMultipartFile("file", "photo.png", claimedType, bytes))
                        .header("X-API-Key", key))
                .andReturn().getResponse();
    }

    @Test
    void uploadedPhotoIsServedPubliclyAndUsedByTheProduct() throws Exception {
        MockHttpServletResponse uploaded = upload(apiKey, businessId, PNG, "image/png");
        assertEquals(201, uploaded.getStatus());
        JsonNode upload = json(uploaded);
        String id = upload.get("id").asString();
        assertEquals("http://localhost:8080/media/" + id, upload.get("url").asString());

        MockHttpServletResponse served = mockMvc.perform(get("/media/" + id)).andReturn().getResponse();
        assertEquals(200, served.getStatus());
        assertEquals("image/png", served.getContentType());
        assertArrayEquals(PNG, served.getContentAsByteArray());
        assertTrue(served.getHeader("Cache-Control").contains("immutable"));
        assertEquals("nosniff", served.getHeader("X-Content-Type-Options"));

        JsonNode product = json(mockMvc.perform(post("/api/businesses/" + businessId + "/products")
                .header("X-API-Key", apiKey).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Сарнай", "categoryId", categoryId,
                        "price", 15000, "imageFileId", id)))).andReturn().getResponse());
        assertEquals("http://localhost:8080/media/" + id, product.get("imageUrl").asString());

        JsonNode updated = json(mockMvc.perform(put("/api/businesses/" + businessId + "/products/" + product.get("id").asLong())
                .header("X-API-Key", apiKey).contentType(MediaType.APPLICATION_JSON)
                .content("{\"removeImage\":true}")).andReturn().getResponse());
        assertTrue(updated.get("imageUrl").isNull());
    }

    @Test
    void onlyRealImagesAreAccepted() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();

        assertEquals(400, upload(apiKey, businessId, html, "image/png").getStatus(), "the claimed type is ignored");
        assertEquals(400, upload(apiKey, businessId, new byte[0], "image/png").getStatus());
        assertEquals(400, upload(apiKey, businessId, new byte[MediaService.MAX_BYTES + 1], "image/png").getStatus());
    }

    @Test
    void aShopCannotUseAnotherShopsPhoto() throws Exception {
        JsonNode other = register();
        String othersPhoto = json(upload(other.get("apiKey").asString(), other.get("id").asLong(), PNG, "image/png"))
                .get("id").asString();

        assertEquals(400, mockMvc.perform(post("/api/businesses/" + businessId + "/products")
                .header("X-API-Key", apiKey).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "X", "categoryId", categoryId,
                        "price", 1, "imageFileId", othersPhoto)))).andReturn().getResponse().getStatus());
        assertEquals(403, upload(apiKey, other.get("id").asLong(), PNG, "image/png").getStatus());
    }

    @Test
    void unknownPhotoIs404AndUploadNeedsCredentials() throws Exception {
        assertEquals(404, mockMvc.perform(get("/media/" + UUID.randomUUID())).andReturn().getResponse().getStatus());
        assertEquals(401, mockMvc.perform(multipart("/api/businesses/" + businessId + "/media")
                .file(new MockMultipartFile("file", "p.png", "image/png", PNG))).andReturn().getResponse().getStatus());
    }

    @Test
    void cleanupRemovesOldUnusedUploadsOnly() throws Exception {
        UUID unused = UUID.fromString(json(upload(apiKey, businessId, PNG, "image/png")).get("id").asString());
        UUID used = UUID.fromString(json(upload(apiKey, businessId, PNG, "image/png")).get("id").asString());
        UUID fresh = UUID.fromString(json(upload(apiKey, businessId, PNG, "image/png")).get("id").asString());
        mockMvc.perform(post("/api/businesses/" + businessId + "/products")
                .header("X-API-Key", apiKey).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Y", "categoryId", categoryId,
                        "price", 1, "imageFileId", used.toString()))));
        jdbcTemplate.update("UPDATE media_files SET created_at = now() - interval '2 days' WHERE id IN (?, ?)", unused, used);

        mediaService.deleteUnusedUploads();

        assertFalse(mediaFileRepository.existsById(unused));
        assertTrue(mediaFileRepository.existsById(used));
        assertTrue(mediaFileRepository.existsById(fresh), "a photo uploaded just now may be about to be saved");
    }

    @Test
    void recognizesImageFormatsByTheirBytes() {
        assertEquals("image/png", MediaService.detectImageType(PNG));
        assertEquals("image/jpeg", MediaService.detectImageType(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));
        assertEquals("image/gif", MediaService.detectImageType("GIF89a....".getBytes()));
        assertEquals("image/webp", MediaService.detectImageType("RIFF\0\0\0\0WEBPVP8 ".getBytes()));
        assertNull(MediaService.detectImageType("<svg xmlns=".getBytes()), "SVG can carry scripts");
    }
}
