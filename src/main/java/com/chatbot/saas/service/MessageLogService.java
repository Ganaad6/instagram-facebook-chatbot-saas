package com.chatbot.saas.service;

import com.chatbot.saas.dto.response.MessageResponse;
import com.chatbot.saas.entity.Conversation;
import com.chatbot.saas.entity.Message;
import com.chatbot.saas.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Records the conversation transcript - customer messages and the bot's replies - so a
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
        save(conversation, metaMessageId, content, Message.Direction.INBOUND);
    }

    /**
     * Records a reply the bot sent. Only replies Meta accepted (i.e. that have a message id)
     * are recorded, so the transcript reflects what the customer actually received.
     */
    public void recordOutbound(Conversation conversation, String metaMessageId, String content) {
        if (metaMessageId == null) {
            return;
        }
        save(conversation, metaMessageId, content, Message.Direction.OUTBOUND);
    }

    /** The conversation's transcript in the order it happened. Caller must check tenant access. */
    @Transactional(readOnly = true)
    public List<MessageResponse> getTranscript(Long conversationId) {
        return messageRepository.findAllByConversationIdOrderByIdAsc(conversationId).stream()
                .map(MessageResponse::from)
                .toList();
    }

    private void save(Conversation conversation, String metaMessageId, String content, Message.Direction direction) {
        messageRepository.save(Message.builder()
                .conversation(conversation)
                .customer(conversation.getCustomer())
                .business(conversation.getBusiness())
                .messageId(metaMessageId != null ? metaMessageId : UUID.randomUUID().toString())
                .direction(direction)
                .content(content)
                .sentAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build());
    }
}
