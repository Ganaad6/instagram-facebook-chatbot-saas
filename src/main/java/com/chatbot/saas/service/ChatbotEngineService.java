package com.chatbot.saas.service;

import com.chatbot.saas.dto.response.ProductResponse;
import com.chatbot.saas.entity.*;
import com.chatbot.saas.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * State-machine based chatbot engine for the product-order flow.
 *
 * States:
 *   IDLE → AWAITING_CATEGORY → AWAITING_PRODUCT → AWAITING_QUANTITY → AWAITING_CONFIRMATION
 *        → COLLECT_NAME → COLLECT_PHONE → COLLECT_ADDRESS → ORDER_SAVED
 *   AWAITING_CONFIRMATION → AWAITING_CATEGORY (user says no)
 *   any state → AWAITING_CATEGORY on a restart keyword (e.g. "цэс" / "menu")
 *
 * Platform-aware: Facebook gets quick-reply buttons; Instagram gets numbered text lists.
 * Every reply the customer receives is recorded in the conversation transcript.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotEngineService {

    private static final String MONGOLIAN_PHONE_REGEX = "^\\d{8}$";
    private static final Set<String> RESTART_KEYWORDS = Set.of("цэс", "эхлэх", "дахин", "menu", "start", "restart");
    static final int MAX_QUANTITY = 99;
    private static final int QUANTITY_SHORTCUTS = 5;

    private final ConversationRepository conversationRepository;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final OrderService orderService;
    private final OrderNotificationService orderNotificationService;
    private final MetaReplyService metaReplyService;
    private final OAuthService oAuthService;
    private final MessageLogService messageLogService;

    @Value("${directus.public-url:}")
    private String directusPublicUrl;

    /** Everything needed to reply within one processed message. */
    private record Chat(Conversation conversation, String senderId, String token, String platform) {
    }

    @Transactional
    public void process(Conversation conversation, String userInput, String platform) {
        Business business = conversation.getBusiness();
        String token = oAuthService.getDecryptedAccessToken(business);
        if (token == null) {
            log.warn("Business {} has no Meta token; cannot reply to conversation {}", business.getId(), conversation.getId());
            return;
        }
        Chat chat = new Chat(conversation, conversation.getCustomer().getPlatformUserId(platform), token, platform);
        String input = userInput != null ? userInput : "";

        Conversation.State state = conversation.getState();
        log.debug("Processing state={} platform={}", state, platform);

        if (state != Conversation.State.IDLE && isRestart(input)) {
            resetSelections(conversation);
            showCategories(chat);
            return;
        }

        switch (state) {
            case IDLE -> showCategories(chat);
            case AWAITING_CATEGORY -> handleAwaitingCategory(chat, input);
            case AWAITING_PRODUCT -> handleAwaitingProduct(chat, input);
            case AWAITING_QUANTITY -> handleAwaitingQuantity(chat, input);
            case AWAITING_CONFIRMATION -> handleAwaitingConfirmation(chat, input);
            case COLLECT_NAME -> handleCollectName(chat, input);
            case COLLECT_PHONE -> handleCollectPhone(chat, input);
            case COLLECT_ADDRESS -> handleCollectAddress(chat, input);
            default -> log.warn("Conversation {} in terminal state {}, ignoring input", conversation.getId(), state);
        }
    }

    // ─── State Handlers ───────────────────────────────────────────────────────

    private void handleAwaitingCategory(Chat chat, String userInput) {
        List<Category> categories = categoryService.getActiveCategories(chat.conversation().getBusiness().getId());
        if (categories.isEmpty()) {
            reply(chat, "Уучлаарай, одоогоор бараа бүтээгдэхүүн бэлэн болоогүй байна.");
            return;
        }
        Integer choice = parseChoice(userInput);
        if (choice == null || choice < 1 || choice > categories.size()) {
            reply(chat, "Буруу дугаар оруулсан байна. Доорх жагсаалтаас дугаараа бичнэ үү:");
            showCategories(chat);
            return;
        }
        chat.conversation().setSelectedCategory(categories.get(choice - 1));
        saveState(chat.conversation(), Conversation.State.AWAITING_PRODUCT);
        showProducts(chat);
    }

    private void handleAwaitingProduct(Chat chat, String userInput) {
        Conversation conversation = chat.conversation();
        Category category = conversation.getSelectedCategory();
        if (category == null || !Boolean.TRUE.equals(category.getIsActive())) {
            resetSelections(conversation);
            reply(chat, "Уучлаарай, энэ ангилал одоо байхгүй байна.");
            showCategories(chat);
            return;
        }
        List<Product> products = productService.getActiveProductsByCategory(
                conversation.getBusiness().getId(), category.getId());

        if (products.isEmpty()) {
            reply(chat, "Энэ ангиллд бараа байхгүй байна. Буцаж ангилал сонгоно уу (1):");
            showCategories(chat);
            return;
        }
        Integer choice = parseChoice(userInput);
        if (choice == null || choice < 1 || choice > products.size()) {
            reply(chat, "Буруу дугаар. Доорх жагсаалтаас дугаараа бичнэ үү:");
            showProducts(chat);
            return;
        }
        conversation.setSelectedProduct(products.get(choice - 1));
        saveState(conversation, Conversation.State.AWAITING_QUANTITY);
        askQuantity(chat);
    }

    private void handleAwaitingQuantity(Chat chat, String userInput) {
        Integer quantity = parseChoice(userInput);
        if (quantity == null || quantity < 1 || quantity > MAX_QUANTITY) {
            reply(chat, "Тоо ширхэгээ 1-" + MAX_QUANTITY + " хооронд тоогоор бичнэ үү:");
            return;
        }
        chat.conversation().setSelectedQuantity(quantity);
        saveState(chat.conversation(), Conversation.State.AWAITING_CONFIRMATION);
        showConfirmation(chat);
    }

    private void handleAwaitingConfirmation(Chat chat, String userInput) {
        Conversation conversation = chat.conversation();
        String trimmed = userInput.trim();
        boolean confirmed = "1".equals(trimmed) || "тийм".equalsIgnoreCase(trimmed) || "yes".equalsIgnoreCase(trimmed);
        boolean cancelled = "0".equals(trimmed) || "буцах".equalsIgnoreCase(trimmed) || "no".equalsIgnoreCase(trimmed);

        if (confirmed && !isAvailable(conversation.getSelectedProduct())) {
            productNoLongerAvailable(chat);
        } else if (confirmed) {
            saveState(conversation, Conversation.State.COLLECT_NAME);
            reply(chat, "Таны нэрийг оруулна уу:");
        } else if (cancelled) {
            resetSelections(conversation);
            reply(chat, "Буцаж ангилал сонгоно уу:");
            showCategories(chat);
        } else {
            reply(chat, "1 (Тийм) эсвэл 0 (Буцах) гэж оруулна уу:");
        }
    }

    private void handleCollectName(Chat chat, String userInput) {
        if (!StringUtils.hasText(userInput)) {
            reply(chat, "Нэрийг хоосон орхиж болохгүй. Дахин оруулна уу:");
            return;
        }
        chat.conversation().setCollectedName(userInput.trim());
        saveState(chat.conversation(), Conversation.State.COLLECT_PHONE);
        reply(chat, "Утасны дугаараа оруулна уу (8 оронтой):");
    }

    private void handleCollectPhone(Chat chat, String userInput) {
        String phone = userInput.trim().replaceAll("\\s", "");
        if (!phone.matches(MONGOLIAN_PHONE_REGEX)) {
            reply(chat, "Утасны дугаар 8 оронтой байх ёстой. Дахин оруулна уу:");
            return;
        }
        chat.conversation().setCollectedPhone(phone);
        saveState(chat.conversation(), Conversation.State.COLLECT_ADDRESS);
        reply(chat, "Хүргэлтийн хаягаа оруулна уу:");
    }

    private void handleCollectAddress(Chat chat, String userInput) {
        Conversation conversation = chat.conversation();
        if (!StringUtils.hasText(userInput)) {
            reply(chat, "Хаягаа оруулна уу:");
            return;
        }
        conversation.setCollectedAddress(userInput.trim());
        if (!isAvailable(conversation.getSelectedProduct())) {
            productNoLongerAvailable(chat);
            return;
        }

        Order.Platform orderPlatform = "FACEBOOK".equalsIgnoreCase(chat.platform())
                ? Order.Platform.FACEBOOK : Order.Platform.INSTAGRAM;

        Order order = orderService.createOrder(
                conversation.getBusiness(),
                conversation.getCustomer(),
                conversation.getSelectedProduct(),
                quantityOf(conversation),
                conversation.getCollectedName(),
                conversation.getCollectedPhone(),
                conversation.getCollectedAddress(),
                orderPlatform
        );

        conversation.setOrder(order);
        conversation.setStatus(Conversation.Status.COMPLETED);
        conversation.setCompletedAt(LocalDateTime.now());
        saveState(conversation, Conversation.State.ORDER_SAVED);

        reply(chat, String.format(
                "✅ Таны захиалга амжилттай бүртгэгдлээ!\n" +
                "📦 Бүтээгдэхүүн: %s × %d\n" +
                "💰 Нийт үнэ: %s\n" +
                "📞 Утас: %s\n" +
                "📍 Хаяг: %s\n" +
                "Бид тантай удахгүй холбогдоно. Баярлалаа! 🙏",
                order.getProductName(),
                order.getQuantity(),
                formatPrice(order.getTotalAmount()),
                conversation.getCollectedPhone(),
                conversation.getCollectedAddress()
        ));

        // Async notification to business
        orderNotificationService.notifyNewOrder(conversation.getBusiness(), order);
        log.info("Order {} created and conversation {} completed", order.getId(), conversation.getId());
    }

    // ─── Menu Builders ────────────────────────────────────────────────────────

    private void showCategories(Chat chat) {
        List<Category> categories = categoryService.getActiveCategories(chat.conversation().getBusiness().getId());
        if (categories.isEmpty()) {
            reply(chat, "Уучлаарай, одоогоор захиалах боломжтой бараа байхгүй байна.");
            return;
        }
        List<String> items = categories.stream().map(Category::getName).collect(Collectors.toList());
        saveState(chat.conversation(), Conversation.State.AWAITING_CATEGORY);
        replyMenu(chat, "Сайн байна уу! 👋 Та юу захиалах вэ? Ангиллаа сонгоно уу:", items);
    }

    private void showProducts(Chat chat) {
        Category category = chat.conversation().getSelectedCategory();
        List<Product> products = productService.getActiveProductsByCategory(
                chat.conversation().getBusiness().getId(), category.getId());
        if (products.isEmpty()) {
            reply(chat, "Энэ ангиллд бараа байхгүй байна.");
            return;
        }
        for (Product product : products) {
            String imageUrl = ProductResponse.resolveImageUrl(product.getImageFileId(), directusPublicUrl);
            if (imageUrl != null) {
                String messageId = metaReplyService.sendImage(chat.senderId(), imageUrl, chat.token());
                messageLogService.recordOutbound(chat.conversation(), messageId, "[image] " + imageUrl);
            }
        }

        List<String> items = products.stream()
                .map(p -> p.getName() + " — " + formatPrice(p.getPrice()))
                .collect(Collectors.toList());
        replyMenu(chat, category.getName() + " 🛍️ Бүтээгдэхүүнээ сонгоно уу:", items);
    }

    private void askQuantity(Chat chat) {
        Product product = chat.conversation().getSelectedProduct();
        String text = String.format("'%s' — %s. Хэдэн ширхэг авах вэ? (1-%d)",
                product.getName(), formatPrice(product.getPrice()), MAX_QUANTITY);
        if ("FACEBOOK".equalsIgnoreCase(chat.platform())) {
            List<Map<String, String>> shortcuts = IntStream.rangeClosed(1, QUANTITY_SHORTCUTS)
                    .mapToObj(String::valueOf)
                    .map(n -> Map.of("title", n, "payload", n))
                    .collect(Collectors.toList());
            String messageId = metaReplyService.sendWithQuickReplies(chat.senderId(), text, shortcuts, chat.token());
            messageLogService.recordOutbound(chat.conversation(), messageId, text);
        } else {
            reply(chat, text);
        }
    }

    private void showConfirmation(Chat chat) {
        Conversation conversation = chat.conversation();
        Product product = conversation.getSelectedProduct();
        int quantity = quantityOf(conversation);
        BigDecimal total = product.getPrice().multiply(BigDecimal.valueOf(quantity));
        reply(chat, String.format(
                "Та '%s' × %d — нийт %s захиалах гэж байна.\nЗөв үү?\n1. Тийм ✅\n0. Буцах 🔙",
                product.getName(), quantity, formatPrice(total)));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void reply(Chat chat, String text) {
        String messageId = metaReplyService.sendText(chat.senderId(), text, chat.token());
        messageLogService.recordOutbound(chat.conversation(), messageId, text);
    }

    private void replyMenu(Chat chat, String introText, List<String> items) {
        String messageId = metaReplyService.sendMenuMessage(chat.senderId(), chat.platform(), introText, items, chat.token());
        messageLogService.recordOutbound(chat.conversation(), messageId, MetaReplyService.renderMenuText(introText, items));
    }

    private static String formatPrice(BigDecimal amount) {
        return String.format("₮%,.0f", amount.doubleValue());
    }

    /** Conversations that reached confirmation before quantities existed default to 1. */
    private static int quantityOf(Conversation conversation) {
        return conversation.getSelectedQuantity() != null ? conversation.getSelectedQuantity() : 1;
    }

    private boolean isAvailable(Product product) {
        return product != null && Boolean.TRUE.equals(product.getIsActive());
    }

    /** The shop deactivated the chosen product mid-conversation; send the customer back to the menu. */
    private void productNoLongerAvailable(Chat chat) {
        resetSelections(chat.conversation());
        reply(chat, "Уучлаарай, энэ бараа дууссан байна. Өөр бараа сонгоно уу.");
        showCategories(chat);
    }

    private boolean isRestart(String input) {
        return RESTART_KEYWORDS.contains(input.trim().toLowerCase(Locale.ROOT));
    }

    private void resetSelections(Conversation conversation) {
        conversation.setSelectedCategory(null);
        conversation.setSelectedProduct(null);
        conversation.setSelectedQuantity(null);
    }

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
