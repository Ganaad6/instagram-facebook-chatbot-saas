package com.chatbot.saas.repository;

import com.chatbot.saas.entity.MediaFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MediaFileRepository extends JpaRepository<MediaFile, UUID> {

    @Query("SELECT m.id FROM MediaFile m WHERE m.id IN :ids")
    List<UUID> findExistingIds(@Param("ids") Collection<UUID> ids);

    boolean existsByIdAndBusinessId(UUID id, Long businessId);

    /** Uploads that no product uses (replaced photos, abandoned uploads). */
    @Modifying
    @Query(value = "DELETE FROM media_files m WHERE m.created_at < :before "
            + "AND NOT EXISTS (SELECT 1 FROM products p WHERE p.image_file_id = m.id)", nativeQuery = true)
    int deleteUnreferencedBefore(@Param("before") LocalDateTime before);
}
