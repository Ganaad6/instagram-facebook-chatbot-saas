package com.chatbot.saas.service;

import com.chatbot.saas.entity.Business;
import com.chatbot.saas.entity.DataDeletionRequest;
import com.chatbot.saas.repository.BusinessRepository;
import com.chatbot.saas.repository.DataDeletionRequestRepository;
import com.chatbot.saas.util.ApiKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Meta's deauthorize and data-deletion callbacks, for the Facebook user who connected a shop's
 * Page. What the platform holds about that person is the connection itself: their app-scoped id,
 * the Page token and the Page / Instagram ids. The shop's catalog, orders and chats belong to the
 * shop and are not touched here (see the privacy policy).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetaDataDeletionService {

    private final BusinessRepository businessRepository;
    private final DataDeletionRequestRepository dataDeletionRequestRepository;

    /** The person removed the app from their Facebook account: its Page tokens no longer work. */
    @Transactional
    public int deauthorize(String metaUserId) {
        int removed = disconnect(metaUserId);
        log.info("Meta deauthorize: removed {} Facebook connection(s)", removed);
        return removed;
    }

    /** Deletes the person's connection data and returns the confirmation code for the status page. */
    @Transactional
    public DataDeletionRequest deleteUserData(String metaUserId) {
        int removed = disconnect(metaUserId);
        DataDeletionRequest request = dataDeletionRequestRepository.save(DataDeletionRequest.builder()
                .confirmationCode(ApiKeyGenerator.randomToken().substring(0, 20))
                .connectionsRemoved(removed)
                .completedAt(LocalDateTime.now())
                .build());
        log.info("Meta data deletion {}: removed {} Facebook connection(s)", request.getConfirmationCode(), removed);
        return request;
    }

    @Transactional(readOnly = true)
    public Optional<DataDeletionRequest> findRequest(String confirmationCode) {
        return dataDeletionRequestRepository.findByConfirmationCode(confirmationCode);
    }

    private int disconnect(String metaUserId) {
        List<Business> businesses = businessRepository.findAllByMetaUserId(metaUserId);
        for (Business business : businesses) {
            business.setMetaUserId(null);
            business.setAccessToken(null);
            business.setTokenExpiresAt(null);
            business.setFacebookPageId(null);
            business.setInstagramAccountId(null);
            log.info("Business {} disconnected from Facebook at the user's request", business.getId());
        }
        businessRepository.saveAll(businesses);
        return businesses.size();
    }
}
