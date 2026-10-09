package com.chatbot.saas.repository;

import com.chatbot.saas.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessRepository extends JpaRepository<Business, Long> {
    Optional<Business> findByInstagramAccountId(String instagramAccountId);
    Optional<Business> findByFacebookPageId(String facebookPageId);
    Optional<Business> findByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<Business> findByApiKeyHash(String apiKeyHash);
    List<Business> findAllByMetaUserId(String metaUserId);
}
