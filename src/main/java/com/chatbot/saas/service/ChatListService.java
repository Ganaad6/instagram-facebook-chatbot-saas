package com.chatbot.saas.service;

import com.chatbot.saas.dto.response.ChatSummaryResponse;
import com.chatbot.saas.entity.Customer;
import com.chatbot.saas.entity.Message;
import com.chatbot.saas.entity.Order;
import com.chatbot.saas.repository.CustomerRepository;
import com.chatbot.saas.repository.MessageRepository;
import com.chatbot.saas.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** The dashboard's list of customer conversations, most recent first. Caller checks tenant access. */
@Service
@RequiredArgsConstructor
public class ChatListService {

    private static final int PREVIEW_LENGTH = 120;

    private final CustomerRepository customerRepository;
    private final MessageRepository messageRepository;
    private final OrderRepository orderRepository;

    /** With waitingOnly, just the customers waiting for a person, longest-waiting first. */
    @Transactional(readOnly = true)
    public Page<ChatSummaryResponse> list(Long businessId, boolean waitingOnly, int page, int size) {
        if (waitingOnly) {
            PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Order.asc("handoffRequestedAt"), Sort.Order.asc("id")));
            return customerRepository.findAllByBusinessIdAndHandoffRequestedAtIsNotNull(businessId, pageable).map(this::summarize);
        }
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("lastInteractionAt").nullsLast(), Sort.Order.desc("id")));
        return customerRepository.findAllByBusinessIdAndErasedAtIsNull(businessId, pageable).map(this::summarize);
    }

    @Transactional(readOnly = true)
    public ChatSummaryResponse get(Long businessId, Long customerId) {
        return customerRepository.findById(customerId)
                .filter(c -> c.getBusiness().getId().equals(businessId) && c.getErasedAt() == null)
                .map(this::summarize)
                .orElseThrow(() -> new com.chatbot.saas.exception.CustomerNotFoundException(customerId));
    }

    private ChatSummaryResponse summarize(Customer customer) {
        Optional<Message> last = messageRepository.findFirstByCustomerIdOrderByIdDesc(customer.getId());
        String displayName = orderRepository.findFirstByCustomerIdAndCustomerNameIsNotNullOrderByIdDesc(customer.getId())
                .map(Order::getCustomerName)
                .orElse(null);
        return ChatSummaryResponse.builder()
                .customerId(customer.getId())
                .displayName(displayName)
                .platform(customer.getFacebookUserId() != null ? "FACEBOOK" : "INSTAGRAM")
                .lastMessage(last.map(m -> preview(m.getContent())).orElse(null))
                .lastMessageSender(last.map(Message::getSenderType).orElse(null))
                .lastMessageAt(last.map(Message::getSentAt).orElse(null))
                .lastInteractionAt(customer.getLastInteractionAt())
                .botPausedUntil(customer.getBotPausedUntil())
                .handoffRequestedAt(customer.getHandoffRequestedAt())
                .build();
    }

    private static String preview(String content) {
        if (content == null) {
            return null;
        }
        return content.length() > PREVIEW_LENGTH ? content.substring(0, PREVIEW_LENGTH) + "…" : content;
    }
}
