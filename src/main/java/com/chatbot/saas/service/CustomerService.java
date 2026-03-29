package com.chatbot.saas.service;

import com.chatbot.saas.dto.response.CustomerResponse;
import com.chatbot.saas.entity.Business;
import com.chatbot.saas.entity.Customer;
import com.chatbot.saas.repository.CustomerRepository;
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
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional
    public Customer findOrCreateCustomer(String instagramUserId, Business business) {
        return customerRepository.findByBusinessIdAndInstagramUserId(business.getId(), instagramUserId)
                .orElseGet(() -> {
                    log.debug("Creating new customer: {} for business: {}", instagramUserId, business.getId());
                    Customer customer = Customer.builder()
                            .business(business)
                            .instagramUserId(instagramUserId)
                            .firstInteractionAt(LocalDateTime.now())
                            .lastInteractionAt(LocalDateTime.now())
                            .build();
                    return customerRepository.save(customer);
                });
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> getCustomersByBusiness(Long businessId) {
        return customerRepository.findAllByBusinessId(businessId)
                .stream()
                .map(CustomerResponse::from)
                .collect(Collectors.toList());
    }
}
