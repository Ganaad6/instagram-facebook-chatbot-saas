package com.chatbot.saas.service;

import com.chatbot.saas.entity.Business;
import com.chatbot.saas.exception.MetaConnectException;
import com.chatbot.saas.repository.BusinessRepository;
import com.chatbot.saas.service.MetaGraphClient.PageAccount;
import com.chatbot.saas.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OAuthServiceTest {

    private static final long BUSINESS_ID = 7L;

    private BusinessRepository businessRepository;
    private MetaGraphClient metaGraphClient;
    private EncryptionUtil encryptionUtil;
    private OAuthService oAuthService;
    private String state;
    private Business business;

    @BeforeEach
    void setUp() {
        businessRepository = mock(BusinessRepository.class);
        metaGraphClient = mock(MetaGraphClient.class);
        encryptionUtil = new EncryptionUtil("0123456789abcdef0123456789abcdef");

        OAuthStateService stateService = new OAuthStateService();
        ReflectionTestUtils.setField(stateService, "secret", "oauth_state_test_secret");
        state = stateService.createState(BUSINESS_ID);

        oAuthService = new OAuthService(businessRepository, metaGraphClient, stateService, encryptionUtil);
        ReflectionTestUtils.setField(oAuthService, "graphApiVersion", "v23.0");

        business = Business.builder().id(BUSINESS_ID).name("Shop").email("shop@example.com")
                .tokenExpiresAt(LocalDateTime.now()).build();
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(business));
        when(businessRepository.findByFacebookPageId(anyString())).thenReturn(Optional.empty());
        when(businessRepository.findByInstagramAccountId(anyString())).thenReturn(Optional.empty());
        when(businessRepository.save(any(Business.class))).thenAnswer(inv -> inv.getArgument(0));

        when(metaGraphClient.exchangeCodeForUserToken("code")).thenReturn("short-token");
        when(metaGraphClient.exchangeForLongLivedUserToken("short-token")).thenReturn("long-token");
    }

    @Test
    void singlePageIsConnectedWithPageTokenAndSubscribed() {
        when(metaGraphClient.listPages("long-token"))
                .thenReturn(List.of(new PageAccount("page-1", "Shop Page", "page-token", "ig-1")));
        when(metaGraphClient.fetchUserId("long-token")).thenReturn("fb-user-1");

        Business saved = oAuthService.handleCallback("code", state);

        assertEquals("page-1", saved.getFacebookPageId());
        assertEquals("ig-1", saved.getInstagramAccountId());
        assertEquals("page-token", encryptionUtil.decrypt(saved.getAccessToken()));
        assertNull(saved.getTokenExpiresAt(), "page tokens don't expire");
        assertEquals("fb-user-1", saved.getMetaUserId(), "Meta's deletion callbacks name the user by this id");
        verify(metaGraphClient).subscribePageToWebhooks("page-1", "page-token");
    }

    @Test
    void configuredPageIdSelectsAmongMultiplePages() {
        business.setFacebookPageId("page-2");
        when(metaGraphClient.listPages("long-token")).thenReturn(List.of(
                new PageAccount("page-1", "Other Page", "token-1", null),
                new PageAccount("page-2", "Shop Page", "token-2", null)));

        Business saved = oAuthService.handleCallback("code", state);

        assertEquals("page-2", saved.getFacebookPageId());
        assertNull(saved.getInstagramAccountId());
        verify(metaGraphClient).subscribePageToWebhooks("page-2", "token-2");
    }

    @Test
    void multiplePagesWithoutConfiguredPageIsRejected() {
        when(metaGraphClient.listPages("long-token")).thenReturn(List.of(
                new PageAccount("page-1", "A", "token-1", null),
                new PageAccount("page-2", "B", "token-2", null)));

        assertThrows(MetaConnectException.class, () -> oAuthService.handleCallback("code", state));
        verify(metaGraphClient, never()).subscribePageToWebhooks(anyString(), anyString());
        verify(businessRepository, never()).save(any());
    }

    @Test
    void noPagesSharedIsRejected() {
        when(metaGraphClient.listPages("long-token")).thenReturn(List.of());

        assertThrows(MetaConnectException.class, () -> oAuthService.handleCallback("code", state));
        verify(businessRepository, never()).save(any());
    }

    @Test
    void pageAlreadyConnectedToAnotherBusinessIsRejected() {
        when(metaGraphClient.listPages("long-token"))
                .thenReturn(List.of(new PageAccount("page-1", "Shop Page", "page-token", null)));
        Business other = Business.builder().id(99L).build();
        when(businessRepository.findByFacebookPageId("page-1")).thenReturn(Optional.of(other));

        assertThrows(MetaConnectException.class, () -> oAuthService.handleCallback("code", state));
        verify(metaGraphClient, never()).subscribePageToWebhooks(anyString(), anyString());
        verify(businessRepository, never()).save(any());
    }

    @Test
    void invalidStateIsRejectedBeforeCallingMeta() {
        assertThrows(IllegalArgumentException.class, () -> oAuthService.handleCallback("code", "forged"));
        verifyNoInteractions(metaGraphClient);
    }
}
