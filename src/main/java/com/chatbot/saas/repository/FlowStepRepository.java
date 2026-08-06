package com.chatbot.saas.repository;

import com.chatbot.saas.entity.FlowStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FlowStepRepository extends JpaRepository<FlowStep, Long> {
    List<FlowStep> findByFlowIdOrderByStepOrderAsc(Long flowId);
    Optional<FlowStep> findFirstByFlowIdOrderByStepOrderAsc(Long flowId);
}
