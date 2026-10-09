package com.chatbot.saas.service;

import com.chatbot.saas.entity.Customer;
import com.chatbot.saas.repository.ConversationRepository;
import com.chatbot.saas.repository.MessageRepository;
import com.chatbot.saas.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Personal data of the shops' customers (the people chatting with the bot): erasure on request
 * and the retention limit for chat history.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerDataService {

    private final CustomerService customerService;
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final OrderRepository orderRepository;

    /** Chat messages older than this many days are deleted; 0 keeps them forever. */
    @Value("${privacy.message-retention-days:365}")
    private int messageRetentionDays;

    /**
     * Erases a customer at the shop's request (e.g. the customer asked): deletes their chat
     * history and conversations, removes their name, phone and address from orders, and drops
     * their Meta ids. Orders stay, without personal data, as the shop's sales records.
     */
    @Transactional
    public void erase(Long businessId, Long customerId) {
        Customer customer = customerService.lockForBusiness(businessId, customerId);
        int messages = messageRepository.deleteAllByCustomerId(customerId);
        conversationRepository.deleteAllByCustomerId(customerId);
        int orders = orderRepository.anonymizeAllByCustomerId(customerId);
        customer.setFacebookUserId(null);
        customer.setInstagramUserId(null);
        customer.setBotPausedUntil(null);
        customer.setHandoffRequestedAt(null);
        customer.setErasedAt(LocalDateTime.now());
        log.info("Business {} erased customer {} ({} messages deleted, {} orders anonymized)",
                businessId, customerId, messages, orders);
    }

    @Scheduled(cron = "${privacy.retention-cron:0 45 3 * * *}")
    @Transactional
    public void applyRetention() {
        if (messageRetentionDays <= 0) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(messageRetentionDays);
        int messages = messageRepository.deleteAllCreatedBefore(cutoff);
        int conversations = conversationRepository.clearContactDetailsUpdatedBefore(cutoff);
        if (messages > 0 || conversations > 0) {
            log.info("Retention: deleted {} messages and cleared contact details from {} conversations older than {} days",
                    messages, conversations, messageRetentionDays);
        }
    }
}
