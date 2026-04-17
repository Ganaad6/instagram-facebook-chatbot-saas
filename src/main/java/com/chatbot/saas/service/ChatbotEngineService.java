package com.chatbot.saas.service;

import com.chatbot.saas.entity.*;
import com.chatbot.saas.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * State-machine based chatbot engine for the product-order flow.
 *
 * States:
 *   IDLE → AWAITING_CATEGORY → AWAITING_PRODUCT → AWAITING_CONFIRMATION
 *        → COLLECT_NAME → COLLECT_PHONE → COLLECT_ADDRESS → ORDER_SAVED
 *   AWAITING_CONFIRMATION → CANCELLED (user says no)
 *
 * Platform-aware: Facebook uses quick-reply buttons; Instagram uses numbered text lists.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotEngineService {

    private static final String MONGOLIAN_PHONE_REGEX = "^\\d{8}$";

    private final ConversationRepository conversationRepository;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final OrderService orderService;
    private final OrderNotificationService orderNotificationService;
    private final MetaReplyService metaReplyService;

    @Transactional
    public void process(Conversation conversation, String userInput, String platform) {
        Business business = conversation.getBusiness();
        Customer customer = conversation.getCustomer();
        String senderId = customer.getInstagramUserId();
        String token = business.getAccessToken();

        Conversation.State state = conversation.getState();
        log.debug("Processing state={} input='{}' platform={}", state, userInput, platform);

        switch (state) {
            case IDLE -> handleIdle(conversation, senderId, token, platform);
            case AWAITING_CATEGORY -> handleAwaitingCategory(conversation, userInput, senderId, token, platform);
            case AWAITING_PRODUCT -> handleAwaitingProduct(conversation, userInput, senderId, token, platform);
            case AWAITING_CONFIRMATION -> handleAwaitingConfirmation(conversation, userInput, senderId, token, platform);
            case COLLECT_NAME -> handleCollectName(conversation, userInput, senderId, token, platform);
            case COLLECT_PHONE -> handleCollectPhone(conversation, userInput, senderId, token, platform);
            case COLLECT_ADDRESS -> handleCollectAddress(conversation, userInput, senderId, token, platform);
            default -> log.warn("Conversation {} in terminal state {}, ignoring input", conversation.getId(), state);
        }
    }

    // ─── State Handlers ───────────────────────────────────────────────────────

    private void handleIdle(Conversation conversation, String senderId, String token, String platform) {
        showCategories(conversation, senderId, token, platform);
    }

    private void handleAwaitingCategory(Conversation conversation, String userInput,
                                         String senderId, String token, String platform) {
        List<Category> categories = categoryService.getActiveCategories(conversation.getBusiness().getId());
        if (categories.isEmpty()) {
            metaReplyService.sendText(senderId, "Уучлаарай, одоогоор бараа бүтээгдэхүүн бэлэн болоогүй байна.", token);
            return;
        }
        Integer choice = parseChoice(userInput);
        if (choice == null || choice < 1 || choice > categories.size()) {
            metaReplyService.sendText(senderId, "Буруу дугаар оруулсан байна. Доорх жагсаалтаас дугаараа бичнэ үү:", token);
            showCategories(conversation, senderId, token, platform);
            return;
        }
        Category selected = categories.get(choice - 1);
        conversation.setSelectedCategory(selected);
        saveState(conversation, Conversation.State.AWAITING_PRODUCT);
        showProducts(conversation, senderId, token, platform);
    }

    private void handleAwaitingProduct(Conversation conversation, String userInput,
                                        String senderId, String token, String platform) {
        List<Product> products = productService.getActiveProductsByCategory(
                conversation.getBusiness().getId(),
                conversation.getSelectedCategory().getId());

        if (products.isEmpty()) {
            metaReplyService.sendText(senderId, "Энэ ангиллд бараа байхгүй байна. Буцаж ангилал сонгоно уу (1):", token);
            showCategories(conversation, senderId, token, platform);
            return;
        }
        Integer choice = parseChoice(userInput);
        if (choice == null || choice < 1 || choice > products.size()) {
            metaReplyService.sendText(senderId, "Буруу дугаар. Доорх жагсаалтаас дугаараа бичнэ үү:", token);
            showProducts(conversation, senderId, token, platform);
            return;
        }
        Product selected = products.get(choice - 1);
        conversation.setSelectedProduct(selected);
        saveState(conversation, Conversation.State.AWAITING_CONFIRMATION);
        showConfirmation(conversation, senderId, token, platform);
    }

    private void handleAwaitingConfirmation(Conversation conversation, String userInput,
                                             String senderId, String token, String platform) {
        String trimmed = userInput.trim();
        boolean confirmed = "1".equals(trimmed) || "тийм".equalsIgnoreCase(trimmed) || "yes".equalsIgnoreCase(trimmed);
        boolean cancelled = "0".equals(trimmed) || "буцах".equalsIgnoreCase(trimmed) || "no".equalsIgnoreCase(trimmed);

        if (confirmed) {
            saveState(conversation, Conversation.State.COLLECT_NAME);
            metaReplyService.sendText(senderId, "Таны нэрийг оруулна уу:", token);
        } else if (cancelled) {
            conversation.setSelectedProduct(null);
            saveState(conversation, Conversation.State.AWAITING_CATEGORY);
            metaReplyService.sendText(senderId, "Буцаж ангилал сонгоно уу:", token);
            showCategories(conversation, senderId, token, platform);
        } else {
            metaReplyService.sendText(senderId, "1 (Тийм) эсвэл 0 (Буцах) гэж оруулна уу:", token);
        }
    }

    private void handleCollectName(Conversation conversation, String userInput,
                                    String senderId, String token, String platform) {
        if (!StringUtils.hasText(userInput)) {
            metaReplyService.sendText(senderId, "Нэрийг хоосон орхиж болохгүй. Дахин оруулна уу:", token);
            return;
        }
        conversation.setCollectedName(userInput.trim());
        saveState(conversation, Conversation.State.COLLECT_PHONE);
        metaReplyService.sendText(senderId, "Утасны дугаараа оруулна уу (8 оронтой):", token);
    }

    private void handleCollectPhone(Conversation conversation, String userInput,
                                     String senderId, String token, String platform) {
        String phone = userInput.trim().replaceAll("\\s", "");
        if (!phone.matches(MONGOLIAN_PHONE_REGEX)) {
            metaReplyService.sendText(senderId, "Утасны дугаар 8 оронтой байх ёстой. Дахин оруулна уу:", token);
            return;
        }
        conversation.setCollectedPhone(phone);
        saveState(conversation, Conversation.State.COLLECT_ADDRESS);
        metaReplyService.sendText(senderId, "Хүргэлтийн хаягаа оруулна уу:", token);
    }

    private void handleCollectAddress(Conversation conversation, String userInput,
                                       String senderId, String token, String platform) {
        if (!StringUtils.hasText(userInput)) {
            metaReplyService.sendText(senderId, "Хаягаа оруулна уу:", token);
            return;
        }
        conversation.setCollectedAddress(userInput.trim());

        // Determine platform for the Order record
        Order.Platform orderPlatform = "FACEBOOK".equalsIgnoreCase(platform)
                ? Order.Platform.FACEBOOK : Order.Platform.INSTAGRAM;

        Order order = orderService.createOrder(
                conversation.getBusiness(),
                conversation.getCustomer(),
                conversation.getSelectedProduct(),
                conversation.getCollectedName(),
                conversation.getCollectedPhone(),
                conversation.getCollectedAddress(),
                orderPlatform
        );

        conversation.setOrder(order);
        conversation.setStatus(Conversation.Status.COMPLETED);
        conversation.setCompletedAt(LocalDateTime.now());
        saveState(conversation, Conversation.State.ORDER_SAVED);

        // Send confirmation
        Product product = conversation.getSelectedProduct();
        String confirmMsg = String.format(
                "✅ Таны захиалга амжилттай бүртгэгдлээ!\n" +
                "📦 Бүтээгдэхүүн: %s\n" +
                "💰 Үнэ: ₮%,.0f\n" +
                "📞 Утас: %s\n" +
                "📍 Хаяг: %s\n" +
                "Бид тантай удахгүй холбогдоно. Баярлалаа! 🙏",
                product.getName(),
                product.getPrice().doubleValue(),
                conversation.getCollectedPhone(),
                conversation.getCollectedAddress()
        );
        metaReplyService.sendText(senderId, confirmMsg, token);

        // Async notification to business
        orderNotificationService.notifyNewOrder(conversation.getBusiness(), order);
        log.info("Order {} created and conversation {} completed", order.getId(), conversation.getId());
    }

    // ─── Menu Builders ────────────────────────────────────────────────────────

    private void showCategories(Conversation conversation, String senderId, String token, String platform) {
        List<Category> categories = categoryService.getActiveCategories(conversation.getBusiness().getId());
        if (categories.isEmpty()) {
            metaReplyService.sendText(senderId, "Уучлаарай, одоогоор захиалах боломжтой бараа байхгүй байна.", token);
            return;
        }
        List<String> items = categories.stream().map(Category::getName).collect(Collectors.toList());
        saveState(conversation, Conversation.State.AWAITING_CATEGORY);
        metaReplyService.sendMenuMessage(senderId, platform,
                "Сайн байна уу! 👋 Та юу захиалах вэ? Ангиллаа сонгоно уу:", items, token);
    }

    private void showProducts(Conversation conversation, String senderId, String token, String platform) {
        Category category = conversation.getSelectedCategory();
        List<Product> products = productService.getActiveProductsByCategory(
                conversation.getBusiness().getId(), category.getId());
        if (products.isEmpty()) {
            metaReplyService.sendText(senderId, "Энэ ангиллд бараа байхгүй байна.", token);
            return;
        }
        List<String> items = products.stream()
                .map(p -> String.format("%s — ₮%,.0f", p.getName(), p.getPrice().doubleValue()))
                .collect(Collectors.toList());
        metaReplyService.sendMenuMessage(senderId, platform,
                category.getName() + " 🛍️ Бүтээгдэхүүнээ сонгоно уу:", items, token);
    }

    private void showConfirmation(Conversation conversation, String senderId, String token, String platform) {
        Product product = conversation.getSelectedProduct();
        String msg = String.format(
                "Та '%s' — ₮%,.0f захиалах гэж байна.\nЗөв үү?\n1. Тийм ✅\n0. Буцах 🔙",
                product.getName(), product.getPrice().doubleValue());
        metaReplyService.sendText(senderId, msg, token);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void saveState(Conversation conversation, Conversation.State newState) {
        conversation.setState(newState);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    private Integer parseChoice(String input) {
        if (input == null) return null;
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
