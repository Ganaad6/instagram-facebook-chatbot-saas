package com.chatbot.saas.service;

import com.chatbot.saas.dto.response.ConversationDataResponse;
import com.chatbot.saas.entity.Conversation;
import com.chatbot.saas.entity.ConversationData;
import com.chatbot.saas.repository.ConversationDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationDataService {

    private final ConversationDataRepository conversationDataRepository;

    @Transactional
    public void saveData(Conversation conversation, String fieldName, String fieldValue) {
        log.debug("Saving data for conversation {}: {}={}", conversation.getId(), fieldName, fieldValue);
        ConversationData data = ConversationData.builder()
                .conversation(conversation)
                .fieldName(fieldName)
                .fieldValue(fieldValue)
                .collectedAt(LocalDateTime.now())
                .build();
        conversationDataRepository.save(data);
    }

    @Transactional(readOnly = true)
    public List<ConversationDataResponse> getDataByConversation(Long conversationId) {
        return conversationDataRepository.findAllByConversationId(conversationId)
                .stream()
                .map(ConversationDataResponse::from)
                .collect(Collectors.toList());
    }
}
