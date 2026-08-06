package com.chatbot.saas.repository;

import com.chatbot.saas.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BusinessRepository extends JpaRepository<Business, Long> {
    Optional<Business> findByInstagramAccountId(String instagramAccountId);
    Optional<Business> findByFacebookPageId(String facebookPageId);
    Optional<Business> findByEmail(String email);
    Optional<Business> findByApiKeyHash(String apiKeyHash);
}
