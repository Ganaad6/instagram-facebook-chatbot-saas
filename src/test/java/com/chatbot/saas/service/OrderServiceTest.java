package com.chatbot.saas.service;

import com.chatbot.saas.entity.Business;
import com.chatbot.saas.entity.Customer;
import com.chatbot.saas.entity.Order;
import com.chatbot.saas.entity.Product;
import com.chatbot.saas.repository.OrderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderService orderService = new OrderService(orderRepository);

    @Test
    void orderSnapshotsProductNameAndPriceAndComputesTotal() {
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        Product product = Product.builder().id(1L).name("Red shoes").price(new BigDecimal("12500.00")).build();

        Order order = orderService.createOrder(Business.builder().id(1L).build(), Customer.builder().id(2L).build(),
                product, 3, "Bat", "99112233", "UB", Order.Platform.FACEBOOK);

        // Later product edits must not change what this order says was bought
        product.setName("Renamed");
        product.setPrice(new BigDecimal("99999.00"));

        assertEquals("Red shoes", order.getProductName());
        assertEquals(new BigDecimal("12500.00"), order.getUnitPrice());
        assertEquals(3, order.getQuantity());
        assertEquals(new BigDecimal("37500.00"), order.getTotalAmount());
    }

    @Test
    void nonPositiveQuantityIsRejected() {
        Product product = Product.builder().id(1L).name("Red shoes").price(BigDecimal.TEN).build();

        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(
                Business.builder().id(1L).build(), Customer.builder().id(2L).build(),
                product, 0, "Bat", "99112233", "UB", Order.Platform.FACEBOOK));
        verify(orderRepository, never()).save(any());
    }
}
