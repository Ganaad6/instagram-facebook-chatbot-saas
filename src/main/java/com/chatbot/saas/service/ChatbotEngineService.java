package com.chatbot.saas.service;

import com.chatbot.saas.entity.*;
import com.chatbot.saas.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
 *   IDLE → POST_ORDER when the customer still has an open order: they get its status once,
 *        then the bot stays quiet until they ask for the menu (or a person)
 *
 * Input the bot can't use gets a short hint first; on the second miss in a row it offers a
 * person ("оператор") and shows the choices again, after that only the offer.
 *
 * If the shop connected QPay, the order confirmation carries a QPay payment link.
 *
 * Platform-aware: Facebook gets quick-reply buttons; Instagram gets numbered text lists.
 * Every reply the customer receives is recorded in the conversation transcript.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotEngineService {

    private static final String MONGOLIAN_PHONE_REGEX = "^\\d{8}$";
    private static final String HUMAN_HINT = "Асуулт байвал \"оператор\" гэж бичээрэй.";
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
    private final PaymentService paymentService;
    private final MediaService mediaService;


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

        if (state != Conversation.State.IDLE && isRestartKeyword(input)) {
            resetSelections(conversation);
            showCategories(chat);
            return;
        }

        switch (state) {
            case IDLE -> greet(chat);
            case AWAITING_CATEGORY -> handleAwaitingCategory(chat, input);
            case AWAITING_PRODUCT -> handleAwaitingProduct(chat, input);
            case AWAITING_QUANTITY -> handleAwaitingQuantity(chat, input);
            case AWAITING_CONFIRMATION -> handleAwaitingConfirmation(chat, input);
            case COLLECT_NAME -> handleCollectName(chat, input);
            case COLLECT_PHONE -> handleCollectPhone(chat, input);
            case COLLECT_ADDRESS -> handleCollectAddress(chat, input);
            case POST_ORDER -> log.debug("Conversation {} already got its order status; waiting for \"цэс\"", conversation.getId());
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
            rejectInput(chat, userInput, numberRange(categories.size()), () -> showCategories(chat));
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
            reply(chat, "Энэ ангилалд одоогоор бараа алга. Өөр ангилал сонгоно уу:");
            showCategories(chat);
            return;
        }
        Integer choice = parseChoice(userInput);
        if (choice == null || choice < 1 || choice > products.size()) {
            rejectInput(chat, userInput, numberRange(products.size()), () -> showProducts(chat));
            return;
        }
        conversation.setSelectedProduct(products.get(choice - 1));
        saveState(conversation, Conversation.State.AWAITING_QUANTITY);
        askQuantity(chat);
    }

    private void handleAwaitingQuantity(Chat chat, String userInput) {
        Integer quantity = parseChoice(userInput);
        if (quantity == null || quantity < 1 || quantity > MAX_QUANTITY) {
            rejectInput(chat, userInput, "Тоо ширхэгээ 1–" + MAX_QUANTITY + " хооронд тоогоор", () -> askQuantity(chat));
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
            rejectInput(chat, userInput, "1 (Тийм) эсвэл 0 (Буцах) гэж", () -> showConfirmation(chat));
        }
    }

    private void handleCollectName(Chat chat, String userInput) {
        if (!StringUtils.hasText(userInput)) {
            reply(chat, "Нэрээ бичнэ үү:");
            return;
        }
        chat.conversation().setCollectedName(userInput.trim());
        saveState(chat.conversation(), Conversation.State.COLLECT_PHONE);
        reply(chat, "Утасны дугаараа бичнэ үү (8 оронтой):");
    }

    private void handleCollectPhone(Chat chat, String userInput) {
        String phone = normalizePhone(userInput);
        if (phone == null) {
            reply(chat, "Утасны дугаараа 8 оронтойгоор бичнэ үү (жишээ нь 9911 2233):");
            return;
        }
        chat.conversation().setCollectedPhone(phone);
        saveState(chat.conversation(), Conversation.State.COLLECT_ADDRESS);
        reply(chat, "Хүргэлтийн хаягаа бичнэ үү (дүүрэг, хороо, байр, тоот):");
    }

    private void handleCollectAddress(Chat chat, String userInput) {
        Conversation conversation = chat.conversation();
        if (!StringUtils.hasText(userInput)) {
            reply(chat, "Хүргэлтийн хаягаа бичнэ үү:");
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

        String paymentUrl = paymentService.requestPayment(order);
        String nextStep = paymentUrl != null
                ? "💳 Төлбөрөө QPay-ээр доорх холбоосоор төлнө үү:\n" + paymentUrl + "\nТөлбөр орсны дараа бид танд мэдэгдэнэ."
                : "Бид тантай удахгүй холбогдож хүргэлтийг тохиролцоно. Баярлалаа! 🙏";
        reply(chat, String.format(
                "✅ Захиалга #%d бүртгэгдлээ!\n" +
                "📦 %s × %d — %s\n" +
                "👤 %s · 📞 %s\n" +
                "📍 %s\n\n" +
                "%s\n" +
                "%s",
                order.getId(),
                order.getProductName(),
                order.getQuantity(),
                formatPrice(order.getTotalAmount()),
                conversation.getCollectedName(),
                conversation.getCollectedPhone(),
                conversation.getCollectedAddress(),
                nextStep,
                HUMAN_HINT
        ));

        // Async notification to business
        orderNotificationService.notifyNewOrder(conversation.getBusiness(), order);
        log.info("Order {} created and conversation {} completed", order.getId(), conversation.getId());
    }

    // ─── Menu Builders ────────────────────────────────────────────────────────

    /**
     * First message of a conversation. A customer whose earlier order is still open most likely
     * writes about it ("баярлалаа", "хэзээ ирэх вэ?"), so they get that order's status instead
     * of the whole menu again.
     */
    private void greet(Chat chat) {
        Conversation conversation = chat.conversation();
        Optional<Order> open = conversation.getCustomer().getId() != null
                ? orderService.findRecentOpenOrder(conversation.getCustomer().getId())
                : Optional.empty();
        if (open.isEmpty()) {
            showCategories(chat);
            return;
        }
        Order order = open.get();
        StringBuilder text = new StringBuilder(String.format(
                "Сайн байна уу! Таны #%d захиалга (%s × %d) %s.",
                order.getId(), order.getProductName(), order.getQuantity(),
                order.getStatus() == Order.Status.CONFIRMED ? "баталгаажсан" : "бүртгэгдсэн, шалгагдаж байна"));
        if (order.getPaymentStatus() == Order.PaymentStatus.PAID) {
            text.append("\nТөлбөр төлөгдсөн ✅");
        } else if (order.getPaymentStatus() == Order.PaymentStatus.PENDING && StringUtils.hasText(order.getPaymentUrl())) {
            text.append("\n💳 Төлбөр хүлээгдэж байна:\n").append(order.getPaymentUrl());
        }
        text.append("\n\nШинээр захиалах бол \"цэс\", ажилтантай холбогдох бол \"оператор\" гэж бичнэ үү.");
        saveState(conversation, Conversation.State.POST_ORDER);
        reply(chat, text.toString());
    }

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
            reply(chat, "Энэ ангилалд одоогоор бараа алга.");
            return;
        }
        Map<UUID, String> imageUrls = mediaService.imageUrls(products.stream().map(Product::getImageFileId).toList());
        for (Product product : products) {
            String imageUrl = product.getImageFileId() != null ? imageUrls.get(product.getImageFileId()) : null;
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

    /**
     * Input the current step can't use. The first miss gets a short hint (the list is just
     * above); the second offers a person and repeats the choices; after that only the offer,
     * so a customer who keeps asking questions isn't flooded with menus.
     *
     * @param expected what to send, completing "… бичнэ үү" (e.g. "1–3 хооронд дугаар")
     */
    private void rejectInput(Chat chat, String input, String expected, Runnable showChoicesAgain) {
        Conversation conversation = chat.conversation();
        int misses = conversation.getInvalidAttempts() + 1;
        conversation.setInvalidAttempts(misses);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        if (misses == 1) {
            reply(chat, parseChoice(input) != null
                    ? "Ийм дугаар байхгүй байна. " + expected + " бичнэ үү."
                    : "Уучлаарай, би зөвхөн дугаараар захиалга авдаг 🙂 " + expected + " бичнэ үү. " + HUMAN_HINT);
        } else if (misses == 2) {
            reply(chat, "Уучлаарай, ойлгосонгүй. Манай ажилтантай холбогдох бол \"оператор\", "
                    + "эхнээс нь эхлэх бол \"цэс\" гэж бичнэ үү. Эсвэл доороос сонгоорой:");
            showChoicesAgain.run();
        } else {
            reply(chat, "Манай ажилтантай холбогдох бол \"оператор\", цэс харах бол \"цэс\" гэж бичнэ үү.");
        }
    }

    private static String numberRange(int count) {
        return (count == 1 ? "1" : "1–" + count + " хооронд") + " дугаар";
    }

    /**
     * Accepts the ways people write Mongolian numbers - "9911 2233", "9911-2233", "+976 99112233"
     * - and returns the bare 8 digits, or null if it isn't one.
     */
    static String normalizePhone(String input) {
        if (input == null) return null;
        String digits = input.trim().replaceAll("[\\s\\-().]", "");
        if (digits.startsWith("+976")) {
            digits = digits.substring(4);
        } else if (digits.startsWith("976") && digits.length() == 11) {
            digits = digits.substring(3);
        }
        return digits.matches(MONGOLIAN_PHONE_REGEX) ? digits : null;
    }

    private void reply(Chat chat, String text) {
        String messageId = metaReplyService.sendText(chat.senderId(), text, chat.token());
        messageLogService.recordOutbound(chat.conversation(), messageId, text);
    }

    private void replyMenu(Chat chat, String introText, List<String> items) {
        String messageId = metaReplyService.sendMenuMessage(chat.senderId(), chat.platform(), introText, items, chat.token());
        messageLogService.recordOutbound(chat.conversation(), messageId, MetaReplyService.renderMenuText(introText, items));
    }

    static String formatPrice(BigDecimal amount) {
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

    /** Menu keywords ("цэс", "menu", ...) that send the customer back to the category menu. */
    public static boolean isRestartKeyword(String input) {
        return input != null && RESTART_KEYWORDS.contains(input.trim().toLowerCase(Locale.ROOT));
    }

    private void resetSelections(Conversation conversation) {
        conversation.setInvalidAttempts(0);
        conversation.setSelectedCategory(null);
        conversation.setSelectedProduct(null);
        conversation.setSelectedQuantity(null);
    }

    private void saveState(Conversation conversation, Conversation.State newState) {
        if (conversation.getState() != newState) {
            conversation.setInvalidAttempts(0); // a new step starts with a clean slate
        }
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
