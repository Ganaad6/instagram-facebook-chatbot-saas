package com.chatbot.saas.service;

import com.chatbot.saas.dto.response.AnalyticsSummaryResponse;
import com.chatbot.saas.entity.*;
import com.chatbot.saas.repository.BusinessRepository;
import com.chatbot.saas.repository.CategoryRepository;
import com.chatbot.saas.repository.CustomerRepository;
import com.chatbot.saas.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Runs the order analytics queries against the real (migrated) schema. */
@SpringBootTest
@ActiveProfiles("test")
class OrderAnalyticsIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CustomerRepository customerRepository;

    @Test
    void summaryUsesOrderSnapshotsAndExcludesCancelledFromRevenue() {
        String unique = String.valueOf(System.nanoTime());
        Business business = businessRepository.save(Business.builder()
                .name("Shop").email(unique + "@example.com").build());
        Category category = categoryRepository.save(Category.builder().business(business).name("Shoes").build());
        Product product = productRepository.save(Product.builder().business(business).category(category)
                .name("Red shoes").price(new BigDecimal("10000.00")).build());
        Customer customer = customerRepository.save(Customer.builder().business(business)
                .instagramUserId("ig-" + unique)
                .firstInteractionAt(LocalDateTime.now()).lastInteractionAt(LocalDateTime.now()).build());

        orderService.createOrder(business, customer, product, 2, "A", "99112233", "UB", Order.Platform.INSTAGRAM);
        Order cancelled = orderService.createOrder(business, customer, product, 5, "B", "99112233", "UB", Order.Platform.INSTAGRAM);
        orderService.updateOrderStatus(business.getId(), cancelled.getId(), "CANCELLED");

        // Renaming the product afterwards must not change how existing orders are reported
        product.setName("Renamed shoes");
        productRepository.save(product);

        AnalyticsSummaryResponse summary = orderService.getSummary(business.getId());

        assertEquals(2, summary.getTotalOrders());
        assertEquals(0, new BigDecimal("20000").compareTo(summary.getTotalRevenue()));
        assertEquals(1, summary.getTopProducts().size());
        assertEquals("Red shoes", summary.getTopProducts().get(0).getProductName());
        assertEquals(2, summary.getTopProducts().get(0).getOrderCount());
        assertEquals(7, summary.getTopProducts().get(0).getTotalQuantity());
    }
}
