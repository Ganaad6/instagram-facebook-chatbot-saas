package com.chatbot.saas.repository;

import com.chatbot.saas.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByBusinessIdAndInstagramUserId(Long businessId, String instagramUserId);
    List<Customer> findAllByBusinessId(Long businessId);
}
