package com.chatbot.saas.service;

import com.chatbot.saas.dto.response.AnalyticsSummaryResponse;
import com.chatbot.saas.dto.response.DailyOrderCountResponse;
import com.chatbot.saas.dto.response.OrderResponse;
import com.chatbot.saas.entity.Business;
import com.chatbot.saas.entity.Customer;
import com.chatbot.saas.entity.Order;
import com.chatbot.saas.entity.Product;
import com.chatbot.saas.exception.OrderNotFoundException;
import com.chatbot.saas.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentService paymentService;

    @Transactional
    public Order createOrder(Business business, Customer customer, Product product, int quantity,
                             String customerName, String phone, String address,
                             Order.Platform platform) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        Order order = Order.builder()
                .business(business)
                .customer(customer)
                .product(product)
                .productName(product.getName())
                .unitPrice(product.getPrice())
                .quantity(quantity)
                .totalAmount(product.getPrice().multiply(BigDecimal.valueOf(quantity)))
                .customerName(customerName)
                .phone(phone)
                .address(address)
                .status(Order.Status.PENDING)
                .platform(platform)
                .build();
        Order saved = orderRepository.save(order);
        log.info("Order {} created for business {} customer {}", saved.getId(), business.getId(), customer.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByBusiness(Long businessId, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orders = status != null
                ? orderRepository.findAllByBusinessIdAndStatus(businessId, Order.Status.valueOf(status.toUpperCase()), pageable)
                : orderRepository.findAllByBusinessId(businessId, pageable);
        return orders.map(OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long businessId, Long orderId) {
        Order order = orderRepository.findByIdAndBusinessId(orderId, businessId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long businessId, Long orderId, String statusStr) {
        Order order = orderRepository.findByIdAndBusinessId(orderId, businessId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        Order.Status newStatus = Order.Status.valueOf(statusStr.toUpperCase());
        if (newStatus == Order.Status.CANCELLED) {
            paymentService.cancelPendingInvoice(order);
        }
        order.setStatus(newStatus);
        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersForExport(Long businessId, LocalDateTime from, LocalDateTime to) {
        return orderRepository.findAllByBusinessIdAndCreatedAtBetween(businessId, from, to);
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse getSummary(Long businessId) {
        long total = orderRepository.countByBusinessId(businessId);
        long pending = orderRepository.countByBusinessIdAndStatus(businessId, Order.Status.PENDING);
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        long today = orderRepository.countByBusinessIdAndCreatedAtBetween(businessId, startOfToday, LocalDateTime.now());

        Pageable top5 = PageRequest.of(0, 5);
        List<Object[]> topRaw = orderRepository.findTopProductsByBusiness(businessId, top5);
        List<AnalyticsSummaryResponse.TopProductResponse> topProducts = topRaw.stream()
                .map(row -> AnalyticsSummaryResponse.TopProductResponse.builder()
                        .productName((String) row[0])
                        .orderCount(((Number) row[1]).longValue())
                        .totalQuantity(((Number) row[2]).longValue())
                        .build())
                .collect(Collectors.toList());

        return AnalyticsSummaryResponse.builder()
                .totalOrders(total)
                .pendingOrders(pending)
                .todayOrders(today)
                .totalRevenue(orderRepository.sumRevenueByBusinessId(businessId))
                .topProducts(topProducts)
                .build();
    }

    @Transactional(readOnly = true)
    public List<DailyOrderCountResponse> getOrdersByDay(Long businessId, LocalDateTime from, LocalDateTime to) {
        List<Object[]> raw = orderRepository.countOrdersByDay(businessId, from, to);
        return raw.stream()
                .map(row -> DailyOrderCountResponse.builder()
                        .day(((java.sql.Date) row[0]).toLocalDate())
                        .count(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());
    }
}
