package com.chatbot.saas.repository;

import com.chatbot.saas.entity.DataDeletionRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DataDeletionRequestRepository extends JpaRepository<DataDeletionRequest, Long> {
    Optional<DataDeletionRequest> findByConfirmationCode(String confirmationCode);
}
