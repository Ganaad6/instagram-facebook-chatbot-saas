package com.chatbot.saas.service;

import com.chatbot.saas.entity.Business;
import com.chatbot.saas.exception.BusinessNotFoundException;
import com.chatbot.saas.exception.MetaConnectException;
import com.chatbot.saas.repository.BusinessRepository;
import com.chatbot.saas.service.MetaGraphClient.PageAccount;
import com.chatbot.saas.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthService {

    private final BusinessRepository businessRepository;
    private final MetaGraphClient metaGraphClient;
    private final OAuthStateService oAuthStateService;
    private final EncryptionUtil encryptionUtil;

    @Value("${meta.app.id}")
    private String appId;

    @Value("${meta.graph.api.version}")
    private String graphApiVersion;

    @Value("${meta.oauth.redirect-uri}")
    private String redirectUri;

    @Value("${meta.oauth.scopes}")
    private String scopes;

    public String generateAuthorizationUrl(Long businessId) {
        String state = oAuthStateService.createState(businessId);
        return UriComponentsBuilder.fromUriString("https://www.facebook.com/" + graphApiVersion + "/dialog/oauth")
                .queryParam("client_id", appId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", scopes)
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    public long getAuthorizationUrlTtlMinutes() {
        return oAuthStateService.getTtlMinutes();
    }

    /**
     * Completes the "connect Facebook/Instagram" flow:
     * code → short-lived user token → long-lived user token → Page token (non-expiring),
     * then subscribes the Page to our webhook and records the Page / Instagram IDs used to
     * route incoming messages to this business.
     *
     * @return the connected business
     */
    public Business handleCallback(String code, String state) {
        Long businessId = oAuthStateService.parseAndValidate(state);
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new BusinessNotFoundException(businessId));
        log.debug("Handling OAuth callback for business {}", businessId);

        String shortLivedToken = metaGraphClient.exchangeCodeForUserToken(code);
        String longLivedToken = metaGraphClient.exchangeForLongLivedUserToken(shortLivedToken);
        PageAccount page = selectPage(business, metaGraphClient.listPages(longLivedToken));

        assertNotLinkedElsewhere(business, page);
        metaGraphClient.subscribePageToWebhooks(page.pageId(), page.pageAccessToken());

        business.setFacebookPageId(page.pageId());
        business.setInstagramAccountId(page.instagramAccountId());
        business.setAccessToken(encryptionUtil.encrypt(page.pageAccessToken()));
        // Page tokens derived from a long-lived user token don't expire; they're only
        // invalidated if the owner revokes access or changes their password (reconnect then).
        business.setTokenExpiresAt(null);
        Business saved = businessRepository.save(business);
        log.info("Business {} connected to page {} (instagram={})",
                businessId, page.pageId(), page.instagramAccountId());
        return saved;
    }

    /**
     * Picks which of the granted Pages belongs to this business. If the business already has a
     * facebookPageId configured, that one must be among the granted Pages; otherwise the owner
     * must have granted exactly one Page so there's no guessing.
     */
    private PageAccount selectPage(Business business, List<PageAccount> pages) {
        List<PageAccount> usable = pages.stream()
                .filter(p -> StringUtils.hasText(p.pageAccessToken()))
                .toList();
        if (usable.isEmpty()) {
            throw new MetaConnectException(
                    "No Facebook Page was shared with the app. Reconnect and select the Page linked to your shop.");
        }

        if (StringUtils.hasText(business.getFacebookPageId())) {
            return usable.stream()
                    .filter(p -> p.pageId().equals(business.getFacebookPageId()))
                    .findFirst()
                    .orElseThrow(() -> new MetaConnectException(
                            "The configured Facebook Page " + business.getFacebookPageId()
                                    + " was not among the Pages shared: " + describe(usable)));
        }

        if (usable.size() > 1) {
            throw new MetaConnectException(
                    "Multiple Facebook Pages were shared (" + describe(usable) + "). Set facebookPageId on "
                            + "the business to the one to use, or reconnect selecting only that Page.");
        }
        return usable.get(0);
    }

    private void assertNotLinkedElsewhere(Business business, PageAccount page) {
        businessRepository.findByFacebookPageId(page.pageId())
                .filter(other -> !other.getId().equals(business.getId()))
                .ifPresent(other -> {
                    throw new MetaConnectException(
                            "Facebook Page " + page.pageId() + " is already connected to another business.");
                });
        if (page.instagramAccountId() != null) {
            businessRepository.findByInstagramAccountId(page.instagramAccountId())
                    .filter(other -> !other.getId().equals(business.getId()))
                    .ifPresent(other -> {
                        throw new MetaConnectException(
                                "Instagram account " + page.instagramAccountId()
                                        + " is already connected to another business.");
                    });
        }
    }

    private String describe(List<PageAccount> pages) {
        return pages.stream()
                .map(p -> p.name() + " [" + p.pageId() + "]")
                .collect(Collectors.joining(", "));
    }

    /**
     * Access tokens are stored AES/GCM-encrypted at rest (see EncryptionUtil); this decrypts
     * for the one purpose they're needed - authenticating outbound calls to the Meta Graph API.
     */
    public String getDecryptedAccessToken(Business business) {
        if (business.getAccessToken() == null) {
            return null;
        }
        return encryptionUtil.decrypt(business.getAccessToken());
    }
}
