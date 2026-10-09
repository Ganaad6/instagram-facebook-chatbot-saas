package com.chatbot.saas.service;

import com.chatbot.saas.dto.response.CustomerResponse;
import com.chatbot.saas.entity.Business;
import com.chatbot.saas.entity.Customer;
import com.chatbot.saas.exception.CustomerNotFoundException;
import com.chatbot.saas.repository.CustomerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final TransactionTemplate newTransaction;

    public CustomerService(CustomerRepository customerRepository, PlatformTransactionManager transactionManager) {
        this.customerRepository = customerRepository;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Finds or creates the customer for a Meta sender and row-locks it for the rest of the
     * caller's transaction, so concurrent messages from the same person are processed one at
     * a time rather than racing on the same conversation.
     */
    @Transactional
    public Customer findOrCreateAndLock(Business business, String platformUserId, String platform) {
        Customer customer = findOrCreateAndLockWithoutTouching(business, platformUserId, platform);
        customer.setLastInteractionAt(LocalDateTime.now());
        return customer;
    }

    /**
     * Same lock, for events that aren't the customer writing (e.g. an echo of a staff reply),
     * so lastInteractionAt keeps meaning "when the customer last wrote".
     */
    @Transactional
    public Customer findOrCreateAndLockWithoutTouching(Business business, String platformUserId, String platform) {
        Long customerId = find(business.getId(), platformUserId, platform)
                .map(Customer::getId)
                .orElseGet(() -> create(business, platformUserId, platform));

        return customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new IllegalStateException("Customer disappeared: " + customerId));
    }

    /**
     * Row-locks a customer for a staff action, serializing it with the customer's incoming
     * messages. A customer of another business is reported as not found.
     */
    @Transactional
    public Customer lockForBusiness(Long businessId, Long customerId) {
        return customerRepository.findByIdForUpdate(customerId)
                .filter(c -> c.getBusiness().getId().equals(businessId) && c.getErasedAt() == null)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    /** Throws CustomerNotFoundException unless the customer belongs to the business. */
    @Transactional(readOnly = true)
    public void assertBelongsTo(Long businessId, Long customerId) {
        customerRepository.findById(customerId)
                .filter(c -> c.getBusiness().getId().equals(businessId) && c.getErasedAt() == null)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    /** Customers a staff member should look at: bot paused for them, or they asked for a person. */
    @Transactional(readOnly = true)
    public List<CustomerResponse> getInbox(Long businessId) {
        return customerRepository.findInbox(businessId, LocalDateTime.now()).stream()
                .map(CustomerResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Inserts in its own transaction so that, if a concurrent message created the same
     * customer first, the unique-constraint violation doesn't poison the caller's transaction
     * and we can just read the winner's row.
     */
    private Long create(Business business, String platformUserId, String platform) {
        try {
            return newTransaction.execute(status -> {
                log.debug("Creating new {} customer: {} for business: {}", platform, platformUserId, business.getId());
                Customer.CustomerBuilder builder = Customer.builder()
                        .business(business)
                        .firstInteractionAt(LocalDateTime.now())
                        .lastInteractionAt(LocalDateTime.now());
                if ("FACEBOOK".equalsIgnoreCase(platform)) {
                    builder.facebookUserId(platformUserId);
                } else {
                    builder.instagramUserId(platformUserId);
                }
                return customerRepository.saveAndFlush(builder.build()).getId();
            });
        } catch (DataIntegrityViolationException e) {
            return find(business.getId(), platformUserId, platform)
                    .map(Customer::getId)
                    .orElseThrow(() -> e);
        }
    }

    private Optional<Customer> find(Long businessId, String platformUserId, String platform) {
        return "FACEBOOK".equalsIgnoreCase(platform)
                ? customerRepository.findByBusinessIdAndFacebookUserId(businessId, platformUserId)
                : customerRepository.findByBusinessIdAndInstagramUserId(businessId, platformUserId);
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> getCustomersByBusiness(Long businessId) {
        return customerRepository.findAllByBusinessIdAndErasedAtIsNull(businessId)
                .stream()
                .map(CustomerResponse::from)
                .collect(Collectors.toList());
    }
}
