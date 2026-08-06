package com.chatbot.saas.service;

import com.chatbot.saas.dto.request.BusinessRegistrationRequest;
import com.chatbot.saas.dto.request.BusinessUpdateRequest;
import com.chatbot.saas.dto.response.BusinessRegistrationResponse;
import com.chatbot.saas.dto.response.BusinessResponse;
import com.chatbot.saas.entity.Business;
import com.chatbot.saas.exception.BusinessNotFoundException;
import com.chatbot.saas.repository.BusinessRepository;
import com.chatbot.saas.util.ApiKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessService {

    private final BusinessRepository businessRepository;

    @Transactional
    public BusinessRegistrationResponse registerBusiness(BusinessRegistrationRequest request) {
        log.debug("Registering business: {}", request.getEmail());
        String rawApiKey = ApiKeyGenerator.generate();
        Business business = Business.builder()
                .name(request.getName())
                .email(request.getEmail())
                .instagramAccountId(request.getInstagramAccountId())
                .facebookPageId(request.getFacebookPageId())
                .status(Business.Status.ACTIVE)
                .apiKeyHash(ApiKeyGenerator.hash(rawApiKey))
                .apiKeyCreatedAt(LocalDateTime.now())
                .build();
        Business saved = businessRepository.save(business);
        return BusinessRegistrationResponse.from(saved, rawApiKey);
    }

    @Transactional
    public BusinessRegistrationResponse rotateApiKey(Long id) {
        Business business = findBusinessById(id);
        String rawApiKey = ApiKeyGenerator.generate();
        business.setApiKeyHash(ApiKeyGenerator.hash(rawApiKey));
        business.setApiKeyCreatedAt(LocalDateTime.now());
        Business saved = businessRepository.save(business);
        return BusinessRegistrationResponse.from(saved, rawApiKey);
    }

    @Transactional
    public BusinessResponse suspend(Long id) {
        Business business = findBusinessById(id);
        business.setStatus(Business.Status.INACTIVE);
        return BusinessResponse.from(businessRepository.save(business));
    }

    @Transactional
    public BusinessResponse activate(Long id) {
        Business business = findBusinessById(id);
        business.setStatus(Business.Status.ACTIVE);
        return BusinessResponse.from(businessRepository.save(business));
    }

    @Transactional(readOnly = true)
    public BusinessResponse getBusinessById(Long id) {
        return BusinessResponse.from(findBusinessById(id));
    }

    @Transactional(readOnly = true)
    public List<BusinessResponse> getAllBusinesses() {
        return businessRepository.findAll().stream()
                .map(BusinessResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public BusinessResponse updateBusiness(Long id, BusinessUpdateRequest request) {
        Business business = findBusinessById(id);
        if (request.getName() != null) business.setName(request.getName());
        if (request.getEmail() != null) business.setEmail(request.getEmail());
        if (request.getInstagramAccountId() != null) business.setInstagramAccountId(request.getInstagramAccountId());
        if (request.getFacebookPageId() != null) business.setFacebookPageId(request.getFacebookPageId());
        return BusinessResponse.from(businessRepository.save(business));
    }

    @Transactional
    public void deleteBusiness(Long id) {
        Business business = findBusinessById(id);
        businessRepository.delete(business);
    }

    public Optional<Business> findByInstagramAccountId(String instagramAccountId) {
        return businessRepository.findByInstagramAccountId(instagramAccountId);
    }

    public Optional<Business> findByFacebookPageId(String facebookPageId) {
        return businessRepository.findByFacebookPageId(facebookPageId);
    }

    public Optional<Business> findByApiKeyHash(String apiKeyHash) {
        return businessRepository.findByApiKeyHash(apiKeyHash);
    }

    public Business findBusinessById(Long id) {
        return businessRepository.findById(id)
                .orElseThrow(() -> new BusinessNotFoundException(id));
    }
}
