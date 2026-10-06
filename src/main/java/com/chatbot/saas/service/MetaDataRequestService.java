package com.chatbot.saas.service;

import com.chatbot.saas.entity.Business;
import com.chatbot.saas.entity.DataDeletionRequest;
import com.chatbot.saas.repository.BusinessRepository;
import com.chatbot.saas.repository.DataDeletionRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Handles Meta's deauthorize and data-deletion callbacks for the Facebook user who connected a
 * shop. What we hold about that person is the connection itself - their app-scoped id and the
 * Page token they granted - so both callbacks remove it; the shop's own records (products,
 * orders, chats with its customers) belong to the shop and stay.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetaDataRequestService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final BusinessRepository businessRepository;
    private final DataDeletionRequestRepository deletionRequestRepository;

    /** The person removed the app from their Facebook settings; its tokens no longer work. */
    @Transactional
    public int deauthorize(String metaUserId) {
        int disconnected = disconnect(metaUserId);
        log.info("Meta deauthorize: disconnected {} shop(s)", disconnected);
        return disconnected;
    }

    @Transactional
    public DataDeletionRequest deleteUserData(String metaUserId) {
        int disconnected = disconnect(metaUserId);
        LocalDateTime now = LocalDateTime.now();
        DataDeletionRequest request = deletionRequestRepository.save(DataDeletionRequest.builder()
                .confirmationCode(HexFormat.of().formatHex(randomBytes()))
                .shopsAffected(disconnected)
                .requestedAt(now)
                .completedAt(now)
                .build());
        log.info("Meta data deletion {}: removed the Facebook connection of {} shop(s)",
                request.getConfirmationCode(), disconnected);
        return request;
    }

    public Optional<DataDeletionRequest> findRequest(String confirmationCode) {
        return deletionRequestRepository.findByConfirmationCode(confirmationCode);
    }

    private int disconnect(String metaUserId) {
        List<Business> shops = businessRepository.findByMetaUserId(metaUserId);
        for (Business shop : shops) {
            shop.setMetaUserId(null);
            shop.setAccessToken(null);
            shop.setTokenExpiresAt(null);
            shop.setFacebookPageId(null);
            shop.setInstagramAccountId(null);
        }
        businessRepository.saveAll(shops);
        return shops.size();
    }

    private static byte[] randomBytes() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
