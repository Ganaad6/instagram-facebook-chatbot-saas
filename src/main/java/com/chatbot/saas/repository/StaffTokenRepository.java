package com.chatbot.saas.repository;

import com.chatbot.saas.entity.StaffToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface StaffTokenRepository extends JpaRepository<StaffToken, Long> {

    @Query("SELECT t FROM StaffToken t JOIN FETCH t.staffUser u JOIN FETCH u.business WHERE t.tokenHash = :hash")
    Optional<StaffToken> findByTokenHash(@Param("hash") String hash);

    /** Issuing a new link retires the user's older unused ones. */
    @Modifying
    @Query("UPDATE StaffToken t SET t.usedAt = :now WHERE t.staffUser.id = :userId AND t.usedAt IS NULL")
    void retireUnused(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
