package com.chatbot.saas.service;

import com.chatbot.saas.entity.MediaFile;
import com.chatbot.saas.exception.ValidationException;
import com.chatbot.saas.repository.MediaFileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Product photos: upload, public URLs, and cleanup of photos no product uses any more. The
 * file type is decided from the file's own bytes, never from what the uploader claims.
 */
@Service
@Slf4j
public class MediaService {

    /** Meta accepts image attachments up to 8 MB; keep well under it. */
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private final MediaFileRepository mediaFileRepository;
    private final String baseUrl;
    private final String directusPublicUrl;

    public MediaService(MediaFileRepository mediaFileRepository,
                        @Value("${app.base-url}") String baseUrl,
                        @Value("${directus.public-url:}") String directusPublicUrl) {
        this.mediaFileRepository = mediaFileRepository;
        this.baseUrl = StringUtils.trimTrailingCharacter(baseUrl, '/');
        this.directusPublicUrl = StringUtils.trimTrailingCharacter(directusPublicUrl == null ? "" : directusPublicUrl, '/');
    }

    public record Upload(UUID id, String url) {
    }

    @Transactional
    public Upload store(Long businessId, byte[] data) {
        if (data == null || data.length == 0) {
            throw new ValidationException("Файл хоосон байна");
        }
        if (data.length > MAX_BYTES) {
            throw new ValidationException("Зураг 5MB-аас ихгүй байх ёстой");
        }
        String contentType = detectImageType(data);
        if (contentType == null) {
            throw new ValidationException("Зөвхөн JPEG, PNG, WebP эсвэл GIF зураг оруулна уу");
        }
        MediaFile file = mediaFileRepository.save(MediaFile.builder()
                .id(UUID.randomUUID())
                .businessId(businessId)
                .contentType(contentType)
                .sizeBytes(data.length)
                .data(data)
                .build());
        return new Upload(file.getId(), url(file.getId()));
    }

    @Transactional(readOnly = true)
    public Optional<MediaFile> find(UUID id) {
        return mediaFileRepository.findById(id);
    }

    /** A product may only use the shop's own uploads (or an older Directus photo it already had). */
    @Transactional(readOnly = true)
    public void assertUsable(Long businessId, UUID id) {
        if (id != null && !mediaFileRepository.existsByIdAndBusinessId(id, businessId)) {
            throw new ValidationException("Зураг олдсонгүй");
        }
    }

    /** Public URL of a product photo, or null. */
    @Transactional(readOnly = true)
    public String imageUrl(UUID id) {
        return id == null ? null : imageUrls(List.of(id)).get(id);
    }

    /** Public URLs for many photos with one query; photos without a URL are left out. */
    @Transactional(readOnly = true)
    public Map<UUID, String> imageUrls(Collection<UUID> ids) {
        Set<UUID> wanted = new HashSet<>(ids);
        wanted.remove(null);
        Map<UUID, String> urls = new HashMap<>();
        if (wanted.isEmpty()) {
            return urls;
        }
        Set<UUID> stored = new HashSet<>(mediaFileRepository.findExistingIds(wanted));
        for (UUID id : wanted) {
            if (stored.contains(id)) {
                urls.put(id, url(id));
            } else if (!directusPublicUrl.isEmpty()) {
                // Uploaded through Directus before the dashboard existed
                urls.put(id, directusPublicUrl + "/assets/" + id);
            }
        }
        return urls;
    }

    /** Daily: drop uploads older than a day that no product refers to. */
    @Scheduled(cron = "${media.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void deleteUnusedUploads() {
        int deleted = mediaFileRepository.deleteUnreferencedBefore(LocalDateTime.now().minusDays(1));
        if (deleted > 0) {
            log.info("Deleted {} unused uploaded images", deleted);
        }
    }

    private String url(UUID id) {
        return baseUrl + "/media/" + id;
    }

    /** Recognizes the image formats Meta can send, by their magic bytes. */
    static String detectImageType(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return "image/png";
        }
        if (d.length >= 6 && d[0] == 'G' && d[1] == 'I' && d[2] == 'F' && d[3] == '8' && (d[4] == '7' || d[4] == '9') && d[5] == 'a') {
            return "image/gif";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
