package com.chatbot.saas.service;

import com.chatbot.saas.dto.request.BusinessRegistrationRequest;
import com.chatbot.saas.dto.response.BusinessRegistrationResponse;
import com.chatbot.saas.entity.Business;
import com.chatbot.saas.repository.BusinessRepository;
import com.chatbot.saas.util.ApiKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessServiceTest {

    @Mock
    private BusinessRepository businessRepository;

    private BusinessService businessService;

    @BeforeEach
    void setUp() {
        businessService = new BusinessService(businessRepository);
    }

    @Test
    void registrationGeneratesApiKeyAndOnlyPersistsItsHash() {
        BusinessRegistrationRequest request = new BusinessRegistrationRequest();
        request.setName("Acme Shop");
        request.setEmail("acme@example.com");

        ArgumentCaptor<Business> savedCaptor = ArgumentCaptor.forClass(Business.class);
        when(businessRepository.save(savedCaptor.capture())).thenAnswer(invocation -> {
            Business business = invocation.getArgument(0);
            business.setId(1L);
            return business;
        });

        BusinessRegistrationResponse response = businessService.registerBusiness(request);

        assertNotNull(response.getApiKey());
        assertTrue(response.getApiKey().startsWith("sk_live_"));

        Business persisted = savedCaptor.getValue();
        assertEquals(ApiKeyGenerator.hash(response.getApiKey()), persisted.getApiKeyHash());
        assertNotEquals(response.getApiKey(), persisted.getApiKeyHash(),
                "raw API key must never be persisted, only its hash");
    }

    @Test
    void registrationProducesUniqueKeysAcrossCalls() {
        BusinessRegistrationRequest request = new BusinessRegistrationRequest();
        request.setName("Acme Shop");
        request.setEmail("acme@example.com");
        when(businessRepository.save(any(Business.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String firstKey = businessService.registerBusiness(request).getApiKey();
        String secondKey = businessService.registerBusiness(request).getApiKey();

        assertNotEquals(firstKey, secondKey);
    }

    @Test
    void suspendSetsStatusToInactive() {
        Business business = Business.builder().id(1L).status(Business.Status.ACTIVE).build();
        when(businessRepository.findById(1L)).thenReturn(Optional.of(business));
        when(businessRepository.save(any(Business.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = businessService.suspend(1L);

        assertEquals(Business.Status.INACTIVE, response.getStatus());
    }

    @Test
    void activateSetsStatusToActive() {
        Business business = Business.builder().id(1L).status(Business.Status.INACTIVE).build();
        when(businessRepository.findById(1L)).thenReturn(Optional.of(business));
        when(businessRepository.save(any(Business.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = businessService.activate(1L);

        assertEquals(Business.Status.ACTIVE, response.getStatus());
    }
}
