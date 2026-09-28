package com.chatbot.saas.service;

import com.chatbot.saas.dto.response.MessageResponse;
import com.chatbot.saas.entity.Conversation;
import com.chatbot.saas.entity.Customer;
import com.chatbot.saas.entity.Message;
import com.chatbot.saas.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Records the transcript - customer messages, the bot's replies and staff replies - so a
 * conversation's history can be read back in full.
 */
@Service
@RequiredArgsConstructor
public class MessageLogService {

    private final MessageRepository messageRepository;

    /** True if a message with this Meta message id was already stored (a webhook redelivery). */
    public boolean isAlreadyRecorded(Long businessId, String metaMessageId) {
        return metaMessageId != null && messageRepository.existsByBusinessIdAndMessageId(businessId, metaMessageId);
    }

    public void recordInbound(Conversation conversation, String metaMessageId, String content) {
        save(conversation.getCustomer(), conversation, metaMessageId, content,
                Message.Direction.INBOUND, Message.SenderType.CUSTOMER);
    }

    /**
     * Records a reply the bot sent. Only replies Meta accepted (i.e. that have a message id)
     * are recorded, so the transcript reflects what the customer actually received.
     */
    public void recordOutbound(Conversation conversation, String metaMessageId, String content) {
        if (metaMessageId == null) {
            return;
        }
        save(conversation.getCustomer(), conversation, metaMessageId, content,
                Message.Direction.OUTBOUND, Message.SenderType.BOT);
    }

    /** Records a message a staff member sent, via the API or the shop's Meta inbox. */
    public void recordAgent(Customer customer, Conversation conversation, String metaMessageId, String content) {
        save(customer, conversation, metaMessageId, content, Message.Direction.OUTBOUND, Message.SenderType.AGENT);
    }

    /** When the customer last wrote to the shop - Meta's reply windows are counted from this. */
    public Optional<LocalDateTime> lastInboundAt(Long customerId) {
        return messageRepository.findFirstByCustomerIdAndDirectionOrderByIdDesc(customerId, Message.Direction.INBOUND)
                .map(Message::getSentAt);
    }

    /** The conversation's transcript in the order it happened. Caller must check tenant access. */
    @Transactional(readOnly = true)
    public List<MessageResponse> getTranscript(Long conversationId) {
        return messageRepository.findAllByConversationIdOrderByIdAsc(conversationId).stream()
                .map(MessageResponse::from)
                .toList();
    }

    /**
     * The customer's most recent messages across all their conversations, oldest first.
     * Caller must check tenant access.
     */
    @Transactional(readOnly = true)
    public List<MessageResponse> getCustomerHistory(Long customerId, int limit) {
        List<MessageResponse> newestFirst = new ArrayList<>(messageRepository
                .findAllByCustomerIdOrderByIdDesc(customerId, PageRequest.of(0, limit)).stream()
                .map(MessageResponse::from)
                .toList());
        Collections.reverse(newestFirst);
        return newestFirst;
    }

    private void save(Customer customer, Conversation conversation, String metaMessageId, String content,
                      Message.Direction direction, Message.SenderType senderType) {
        messageRepository.save(Message.builder()
                .conversation(conversation)
                .customer(customer)
                .business(customer.getBusiness())
                .messageId(metaMessageId != null ? metaMessageId : UUID.randomUUID().toString())
                .direction(direction)
                .senderType(senderType)
                .content(content)
                .sentAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build());
    }
}
