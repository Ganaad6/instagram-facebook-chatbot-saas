package com.chatbot.saas.service;

import com.chatbot.saas.dto.request.BusinessRegistrationRequest;
import com.chatbot.saas.dto.request.BusinessUpdateRequest;
import com.chatbot.saas.dto.response.BusinessResponse;
import com.chatbot.saas.entity.Business;
import com.chatbot.saas.exception.BusinessNotFoundException;
import com.chatbot.saas.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessService {

    private final BusinessRepository businessRepository;

    @Transactional
    public BusinessResponse registerBusiness(BusinessRegistrationRequest request) {
        log.debug("Registering business: {}", request.getEmail());
        Business business = Business.builder()
                .name(request.getName())
                .email(request.getEmail())
                .instagramAccountId(request.getInstagramAccountId())
                .facebookPageId(request.getFacebookPageId())
                .status(Business.Status.ACTIVE)
                .build();
        return BusinessResponse.from(businessRepository.save(business));
    }

    @Transactional(readOnly = true)
    public BusinessResponse getBusinessById(Long id) {
        return BusinessResponse.from(findBusinessById(id));
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

    public Business findBusinessById(Long id) {
        return businessRepository.findById(id)
                .orElseThrow(() -> new BusinessNotFoundException(id));
    }
}
