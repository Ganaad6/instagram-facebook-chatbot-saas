package com.chatbot.saas.service;

import com.chatbot.saas.entity.*;
import com.chatbot.saas.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatbotEngineServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private CategoryService categoryService;
    @Mock
    private ProductService productService;
    @Mock
    private OrderService orderService;
    @Mock
    private OrderNotificationService orderNotificationService;
    @Mock
    private MetaReplyService metaReplyService;
    @Mock
    private OAuthService oAuthService;
    @Mock
    private MessageLogService messageLogService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private MediaService mediaService;

    private ChatbotEngineService chatbotEngineService;

    @BeforeEach
    void setUp() {
        chatbotEngineService = new ChatbotEngineService(
                conversationRepository, categoryService, productService,
                orderService, orderNotificationService, metaReplyService, oAuthService, messageLogService,
                paymentService, mediaService);
    }

    private Product product(Long id, String name, UUID imageFileId) {
        return Product.builder()
                .id(id)
                .name(name)
                .price(BigDecimal.TEN)
                .isActive(true)
                .imageFileId(imageFileId)
                .build();
    }

    private Conversation choosingCategoryWithTwoProducts(UUID imageFileId) {
        Business business = Business.builder().id(1L).build();
        Customer customer = Customer.builder().instagramUserId("customer-1").build();
        Category category = Category.builder().id(10L).name("Shoes").build();
        Conversation conversation = Conversation.builder()
                .business(business)
                .customer(customer)
                .status(Conversation.Status.ACTIVE)
                .state(Conversation.State.AWAITING_CATEGORY)
                .build();
        Product withImage = product(1L, "Red shoes", imageFileId);
        withImage.setDescription("Арьсан, 36-41 размер");
        Product withoutImage = product(2L, "Blue shoes", null);

        when(oAuthService.getDecryptedAccessToken(business)).thenReturn("token");
        when(categoryService.getActiveCategories(1L)).thenReturn(List.of(category));
        when(productService.getActiveProductsByCategory(1L, 10L)).thenReturn(List.of(withImage, withoutImage));
        when(mediaService.imageUrls(anyList())).thenReturn(java.util.Map.of(imageFileId, "https://shop.example/media/" + imageFileId));
        return conversation;
    }

    @Test
    @SuppressWarnings("unchecked")
    void productsGoOutAsCardsWithAnOrderButton() {
        UUID imageFileId = UUID.randomUUID();
        Conversation conversation = choosingCategoryWithTwoProducts(imageFileId);
        when(metaReplyService.sendCards(eq("customer-1"), anyList(), eq("token"))).thenReturn("m_cards");

        chatbotEngineService.process(conversation, "1", "INSTAGRAM");

        org.mockito.ArgumentCaptor<List<MetaReplyService.Card>> cards = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(metaReplyService).sendCards(eq("customer-1"), cards.capture(), eq("token"));
        MetaReplyService.Card first = cards.getValue().get(0);
        assertEquals("1. Red shoes", first.title());
        assertEquals("https://shop.example/media/" + imageFileId, first.imageUrl());
        assertTrue(first.subtitle().contains("₮10") && first.subtitle().contains("Арьсан"), first.subtitle());
        assertEquals("PRODUCT_1", first.payload());
        assertNull(cards.getValue().get(1).imageUrl());
        verify(metaReplyService, never()).sendImage(any(), any(), any());
        verify(metaReplyService).sendText(eq("customer-1"), contains("Захиалах"), eq("token"));
        verify(messageLogService).recordOutbound(eq(conversation), eq("m_cards"), contains("2. Blue shoes"));
    }

    @Test
    void refusedCardsFallBackToPhotosAndANumberedList() {
        UUID imageFileId = UUID.randomUUID();
        Conversation conversation = choosingCategoryWithTwoProducts(imageFileId);
        when(metaReplyService.sendCards(any(), anyList(), any())).thenReturn(null);

        chatbotEngineService.process(conversation, "1", "INSTAGRAM");

        verify(metaReplyService, times(1)).sendImage("customer-1", "https://shop.example/media/" + imageFileId, "token");
        verify(metaReplyService).sendMenuMessage(eq("customer-1"), anyString(), anyList(), eq("token"));
    }

    @Test
    void restartKeywordReturnsToCategoryMenuFromAnyState() {
        Business business = Business.builder().id(1L).build();
        Customer customer = Customer.builder().instagramUserId("customer-1").build();
        Category category = Category.builder().id(10L).name("Shoes").isActive(true).build();
        Conversation conversation = Conversation.builder()
                .business(business).customer(customer)
                .status(Conversation.Status.ACTIVE)
                .state(Conversation.State.COLLECT_PHONE)
                .selectedCategory(category)
                .selectedProduct(product(1L, "Red shoes", null))
                .build();
        when(oAuthService.getDecryptedAccessToken(business)).thenReturn("token");
        when(categoryService.getActiveCategories(1L)).thenReturn(List.of(category));

        chatbotEngineService.process(conversation, " Цэс ", "INSTAGRAM");

        assertEquals(Conversation.State.AWAITING_CATEGORY, conversation.getState());
        assertNull(conversation.getSelectedProduct());
        verify(metaReplyService).sendMenuMessage(eq("customer-1"), anyString(), anyList(), eq("token"));
    }

    @Test
    void confirmingAProductThatWasDeactivatedSendsCustomerBackToMenu() {
        Business business = Business.builder().id(1L).build();
        Customer customer = Customer.builder().instagramUserId("customer-1").build();
        Category category = Category.builder().id(10L).name("Shoes").isActive(true).build();
        Product soldOut = product(1L, "Red shoes", null);
        soldOut.setIsActive(false);
        Conversation conversation = Conversation.builder()
                .business(business).customer(customer)
                .status(Conversation.Status.ACTIVE)
                .state(Conversation.State.AWAITING_CONFIRMATION)
                .selectedCategory(category)
                .selectedProduct(soldOut)
                .build();
        when(oAuthService.getDecryptedAccessToken(business)).thenReturn("token");
        when(categoryService.getActiveCategories(1L)).thenReturn(List.of(category));

        chatbotEngineService.process(conversation, "1", "INSTAGRAM");

        assertEquals(Conversation.State.AWAITING_CATEGORY, conversation.getState());
        assertNull(conversation.getSelectedProduct());
    }

    @Test
    void facebookRepliesGoToTheFacebookSenderId() {
        Business business = Business.builder().id(1L).build();
        Customer customer = Customer.builder().facebookUserId("psid-1").build();
        Category category = Category.builder().id(10L).name("Shoes").isActive(true).build();
        Conversation conversation = Conversation.builder()
                .business(business).customer(customer)
                .status(Conversation.Status.ACTIVE)
                .state(Conversation.State.IDLE)
                .build();
        when(oAuthService.getDecryptedAccessToken(business)).thenReturn("token");
        when(categoryService.getActiveCategories(1L)).thenReturn(List.of(category));

        chatbotEngineService.process(conversation, "hi", "FACEBOOK");

        verify(metaReplyService).sendMenuMessage(eq("psid-1"), anyString(), anyList(), eq("token"));
    }

    private Conversation conversationIn(Conversation.State state, Category category, Product product) {
        return Conversation.builder()
                .business(Business.builder().id(1L).build())
                .customer(Customer.builder().id(5L).instagramUserId("customer-1").build())
                .status(Conversation.Status.ACTIVE)
                .state(state)
                .selectedCategory(category)
                .selectedProduct(product)
                .build();
    }

    @Test
    void choosingAProductAsksForQuantity() {
        Category category = Category.builder().id(10L).name("Shoes").isActive(true).build();
        Conversation conversation = conversationIn(Conversation.State.AWAITING_PRODUCT, category, null);
        when(oAuthService.getDecryptedAccessToken(conversation.getBusiness())).thenReturn("token");
        when(productService.getActiveProductsByCategory(1L, 10L)).thenReturn(List.of(product(1L, "Red shoes", null)));

        chatbotEngineService.process(conversation, "1", "INSTAGRAM");

        assertEquals(Conversation.State.AWAITING_QUANTITY, conversation.getState());
        verify(metaReplyService).sendWithQuickReplies(eq("customer-1"), contains("Хэдэн ширхэг"), anyList(), eq("token"));
    }

    @Test
    void validQuantityShowsConfirmationWithTotal() {
        Conversation conversation = conversationIn(Conversation.State.AWAITING_QUANTITY, null, product(1L, "Red shoes", null));
        when(oAuthService.getDecryptedAccessToken(conversation.getBusiness())).thenReturn("token");

        chatbotEngineService.process(conversation, "3", "INSTAGRAM");

        assertEquals(Conversation.State.AWAITING_CONFIRMATION, conversation.getState());
        assertEquals(3, conversation.getSelectedQuantity());
        verify(metaReplyService).sendWithQuickReplies(eq("customer-1"), contains("× 3 — нийт ₮30"), anyList(), eq("token"));
    }

    @Test
    void outOfRangeQuantityIsRejected() {
        for (String input : List.of("0", "100", "abc", "")) {
            Conversation conversation = conversationIn(Conversation.State.AWAITING_QUANTITY, null, product(1L, "Red shoes", null));
            when(oAuthService.getDecryptedAccessToken(conversation.getBusiness())).thenReturn("token");

            chatbotEngineService.process(conversation, input, "INSTAGRAM");

            assertEquals(Conversation.State.AWAITING_QUANTITY, conversation.getState(), "input: " + input);
            assertNull(conversation.getSelectedQuantity());
        }
    }

    @Test
    void addressStepPlacesOrderWithChosenQuantity() {
        Product product = product(1L, "Red shoes", null);
        Conversation conversation = conversationIn(Conversation.State.COLLECT_ADDRESS, null, product);
        conversation.setSelectedQuantity(3);
        conversation.setCollectedName("Bat");
        conversation.setCollectedPhone("99112233");
        when(oAuthService.getDecryptedAccessToken(conversation.getBusiness())).thenReturn("token");
        Order order = Order.builder().id(42L).productName("Red shoes").quantity(3)
                .unitPrice(BigDecimal.TEN).totalAmount(BigDecimal.valueOf(30)).build();
        when(orderService.createOrder(any(), any(), eq(product), eq(3), eq("Bat"), eq("99112233"),
                eq("Ulaanbaatar"), eq(Order.Platform.INSTAGRAM))).thenReturn(order);

        chatbotEngineService.process(conversation, "Ulaanbaatar", "INSTAGRAM");

        assertEquals(Conversation.State.ORDER_SAVED, conversation.getState());
        assertEquals(Conversation.Status.COMPLETED, conversation.getStatus());
        verify(metaReplyService).sendText(eq("customer-1"), contains("Red shoes × 3"), eq("token"));
        verify(metaReplyService).sendText(eq("customer-1"), contains("Захиалга #42 бүртгэгдлээ"), eq("token"));
        verify(metaReplyService).sendText(eq("customer-1"), contains("\"оператор\""), eq("token"));
        verify(orderNotificationService).notifyNewOrder(conversation.getBusiness(), order);
    }

    // ─── Input the bot can't use ─────────────────────────────────────────────

    private Conversation atCategoryMenu() {
        Conversation conversation = conversationIn(Conversation.State.AWAITING_CATEGORY, null, null);
        when(oAuthService.getDecryptedAccessToken(conversation.getBusiness())).thenReturn("token");
        when(categoryService.getActiveCategories(1L)).thenReturn(List.of(
                Category.builder().id(10L).name("Даашинз").isActive(true).build(),
                Category.builder().id(11L).name("Цүнх").isActive(true).build(),
                Category.builder().id(12L).name("Гутал").isActive(true).build()));
        return conversation;
    }

    @Test
    void aQuestionAtTheMenuGetsAHintNotTheWholeMenu() {
        Conversation conversation = atCategoryMenu();

        chatbotEngineService.process(conversation, "L размер байгаа юу?", "INSTAGRAM");

        verify(metaReplyService).sendText(eq("customer-1"), contains("дугаараар"), eq("token"));
        verify(metaReplyService).sendText(eq("customer-1"), contains("1–3 хооронд дугаар"), eq("token"));
        verify(metaReplyService, never()).sendMenuMessage(any(), any(), anyList(), any());
        assertEquals(1, conversation.getInvalidAttempts());
    }

    @Test
    void anOutOfRangeNumberGetsAShortHint() {
        Conversation conversation = atCategoryMenu();

        chatbotEngineService.process(conversation, "7", "INSTAGRAM");

        verify(metaReplyService).sendText(eq("customer-1"), contains("Ийм дугаар байхгүй"), eq("token"));
    }

    @Test
    void repeatedMissesOfferAPersonAndShowTheMenuOnlyOnce() {
        Conversation conversation = atCategoryMenu();

        chatbotEngineService.process(conversation, "сайн уу", "INSTAGRAM");
        chatbotEngineService.process(conversation, "хүргэлт хэд вэ", "INSTAGRAM");
        chatbotEngineService.process(conversation, "?", "INSTAGRAM");

        verify(metaReplyService).sendText(eq("customer-1"), contains("ойлгосонгүй"), eq("token"));
        verify(metaReplyService, times(3)).sendText(eq("customer-1"), contains("\"оператор\""), eq("token"));
        verify(metaReplyService, times(1)).sendMenuMessage(any(), any(), anyList(), any());
        assertEquals(3, conversation.getInvalidAttempts());
        assertEquals(Conversation.State.AWAITING_CATEGORY, conversation.getState());
    }

    @Test
    void aValidChoiceClearsEarlierMisses() {
        Conversation conversation = atCategoryMenu();
        conversation.setInvalidAttempts(2);
        when(productService.getActiveProductsByCategory(1L, 10L)).thenReturn(List.of(product(1L, "Red shoes", null)));

        chatbotEngineService.process(conversation, "1", "INSTAGRAM");

        assertEquals(Conversation.State.AWAITING_PRODUCT, conversation.getState());
        assertEquals(0, conversation.getInvalidAttempts());
    }

    // ─── After an order ──────────────────────────────────────────────────────

    @Test
    void customerWithAnOpenOrderGetsItsStatusOnceInsteadOfTheMenu() {
        Category category = Category.builder().id(10L).name("Shoes").isActive(true).build();
        Conversation conversation = conversationIn(Conversation.State.IDLE, null, null);
        when(oAuthService.getDecryptedAccessToken(conversation.getBusiness())).thenReturn("token");
        Order open = Order.builder().id(42L).productName("Red shoes").quantity(1).status(Order.Status.PENDING)
                .paymentStatus(Order.PaymentStatus.PENDING).paymentUrl("https://qpay.example/pay/42").build();
        when(orderService.findRecentOpenOrder(5L)).thenReturn(java.util.Optional.of(open));
        when(categoryService.getActiveCategories(1L)).thenReturn(List.of(category));

        chatbotEngineService.process(conversation, "баярлалаа", "INSTAGRAM");

        assertEquals(Conversation.State.POST_ORDER, conversation.getState());
        verify(metaReplyService).sendText(eq("customer-1"), contains("#42"), eq("token"));
        verify(metaReplyService).sendText(eq("customer-1"), contains("https://qpay.example/pay/42"), eq("token"));
        verify(metaReplyService, never()).sendMenuMessage(any(), any(), anyList(), any());

        // Further chatter doesn't repeat the status; the menu keyword still works
        chatbotEngineService.process(conversation, "ок", "INSTAGRAM");
        verify(metaReplyService, times(1)).sendText(any(), any(), any());
        chatbotEngineService.process(conversation, "цэс", "INSTAGRAM");
        assertEquals(Conversation.State.AWAITING_CATEGORY, conversation.getState());
        verify(metaReplyService).sendMenuMessage(eq("customer-1"), anyString(), anyList(), eq("token"));
    }

    @Test
    void customerWithoutAnOpenOrderGetsTheMenu() {
        Category category = Category.builder().id(10L).name("Shoes").isActive(true).build();
        Conversation conversation = conversationIn(Conversation.State.IDLE, null, null);
        when(oAuthService.getDecryptedAccessToken(conversation.getBusiness())).thenReturn("token");
        when(orderService.findRecentOpenOrder(5L)).thenReturn(java.util.Optional.empty());
        when(categoryService.getActiveCategories(1L)).thenReturn(List.of(category));

        chatbotEngineService.process(conversation, "сайн уу", "INSTAGRAM");

        assertEquals(Conversation.State.AWAITING_CATEGORY, conversation.getState());
        verify(metaReplyService).sendMenuMessage(eq("customer-1"), anyString(), anyList(), eq("token"));
    }

    // ─── Product cards ───────────────────────────────────────────────────────

    @Test
    void tappingACardStartsThatProductFromAnyState() {
        Category category = Category.builder().id(10L).name("Shoes").isActive(true).build();
        Product product = product(7L, "Red shoes", null);
        product.setCategory(category);
        product.setDescription("Арьсан, 36-41 размер");
        Conversation conversation = conversationIn(Conversation.State.POST_ORDER, null, null);
        when(oAuthService.getDecryptedAccessToken(conversation.getBusiness())).thenReturn("token");
        when(productService.findOrderable(1L, 7L)).thenReturn(java.util.Optional.of(product));

        chatbotEngineService.process(conversation, "PRODUCT_7", "INSTAGRAM");

        assertEquals(Conversation.State.AWAITING_QUANTITY, conversation.getState());
        assertSame(product, conversation.getSelectedProduct());
        assertSame(category, conversation.getSelectedCategory());
        verify(metaReplyService).sendWithQuickReplies(eq("customer-1"), contains("Арьсан, 36-41 размер"), anyList(), eq("token"));
    }

    @Test
    void tappingACardForAnUnavailableProductShowsTheMenu() {
        Category category = Category.builder().id(10L).name("Shoes").isActive(true).build();
        Conversation conversation = conversationIn(Conversation.State.AWAITING_PRODUCT, category, null);
        when(oAuthService.getDecryptedAccessToken(conversation.getBusiness())).thenReturn("token");
        when(productService.findOrderable(1L, 7L)).thenReturn(java.util.Optional.empty());
        when(categoryService.getActiveCategories(1L)).thenReturn(List.of(category));

        chatbotEngineService.process(conversation, "PRODUCT_7", "INSTAGRAM");

        assertEquals(Conversation.State.AWAITING_CATEGORY, conversation.getState());
        verify(metaReplyService).sendText(eq("customer-1"), contains("боломжгүй"), eq("token"));
    }

    // ─── Delivery details ────────────────────────────────────────────────────

    @Test
    void returningCustomerCanReuseTheirLastDeliveryDetails() {
        Product product = product(1L, "Red shoes", null);
        Conversation conversation = conversationIn(Conversation.State.AWAITING_CONFIRMATION, null, product);
        conversation.setSelectedQuantity(1);
        when(oAuthService.getDecryptedAccessToken(conversation.getBusiness())).thenReturn("token");
        when(orderService.findLatestDeliveryDetails(5L)).thenReturn(java.util.Optional.of(Order.builder()
                .customerName("Болормаа").phone("99112233").address("БЗД, 26-р хороо").build()));

        chatbotEngineService.process(conversation, "1", "INSTAGRAM");

        assertEquals(Conversation.State.CONFIRM_SAVED_DETAILS, conversation.getState());
        verify(metaReplyService).sendWithQuickReplies(eq("customer-1"), contains("БЗД, 26-р хороо"), anyList(), eq("token"));

        Order order = Order.builder().id(43L).productName("Red shoes").quantity(1)
                .unitPrice(BigDecimal.TEN).totalAmount(BigDecimal.TEN).build();
        when(orderService.createOrder(any(), any(), eq(product), eq(1), eq("Болормаа"), eq("99112233"),
                eq("БЗД, 26-р хороо"), eq(Order.Platform.INSTAGRAM))).thenReturn(order);

        chatbotEngineService.process(conversation, "1", "INSTAGRAM");

        assertEquals(Conversation.State.ORDER_SAVED, conversation.getState());
        verify(metaReplyService).sendText(eq("customer-1"), contains("Захиалга #43"), eq("token"));
    }

    @Test
    void returningCustomerCanEnterNewDetails() {
        Conversation conversation = conversationIn(Conversation.State.CONFIRM_SAVED_DETAILS, null, product(1L, "Red shoes", null));
        conversation.setCollectedName("Болормаа");
        conversation.setCollectedPhone("99112233");
        conversation.setCollectedAddress("БЗД");
        when(oAuthService.getDecryptedAccessToken(conversation.getBusiness())).thenReturn("token");

        chatbotEngineService.process(conversation, "2", "INSTAGRAM");

        assertEquals(Conversation.State.COLLECT_NAME, conversation.getState());
        assertNull(conversation.getCollectedAddress());
        verify(metaReplyService).sendText(eq("customer-1"), contains("Нэрээ"), eq("token"));
    }

    @Test
    void newCustomerIsAskedForTheirName() {
        Conversation conversation = conversationIn(Conversation.State.AWAITING_CONFIRMATION, null, product(1L, "Red shoes", null));
        when(oAuthService.getDecryptedAccessToken(conversation.getBusiness())).thenReturn("token");
        when(orderService.findLatestDeliveryDetails(5L)).thenReturn(java.util.Optional.empty());

        chatbotEngineService.process(conversation, "1", "INSTAGRAM");

        assertEquals(Conversation.State.COLLECT_NAME, conversation.getState());
    }

    @Test
    void phoneQuestionOffersTheShareNumberButton() {
        Conversation conversation = conversationIn(Conversation.State.COLLECT_NAME, null, product(1L, "Red shoes", null));
        when(oAuthService.getDecryptedAccessToken(conversation.getBusiness())).thenReturn("token");

        chatbotEngineService.process(conversation, "Bat", "INSTAGRAM");

        verify(metaReplyService).sendPhoneRequest(eq("customer-1"), contains("Утасны дугаар"), eq("token"));
    }

    // ─── The shop's own words ────────────────────────────────────────────────

    @Test
    void firstMenuOpensWithTheShopsGreetingLaterMenusDont() {
        Category category = Category.builder().id(10L).name("Shoes").isActive(true).build();
        Conversation conversation = conversationIn(Conversation.State.IDLE, null, null);
        conversation.getBusiness().setWelcomeMessage("Сайн уу! Шинэ коллекц ирлээ 🌸");
        when(oAuthService.getDecryptedAccessToken(conversation.getBusiness())).thenReturn("token");
        when(categoryService.getActiveCategories(1L)).thenReturn(List.of(category));

        chatbotEngineService.process(conversation, "hi", "INSTAGRAM");
        chatbotEngineService.process(conversation, "цэс", "INSTAGRAM");

        verify(metaReplyService).sendMenuMessage(eq("customer-1"), contains("Шинэ коллекц ирлээ"), anyList(), eq("token"));
        verify(metaReplyService).sendMenuMessage(eq("customer-1"), eq("Ангиллаа сонгоно уу:"), anyList(), eq("token"));
    }

    @Test
    void defaultGreetingNamesTheShop() {
        assertTrue(ChatbotEngineService.welcomeText(Business.builder().name("Сарнай").build()).contains("Сарнай — тавтай морил"));
        assertTrue(ChatbotEngineService.welcomeText(Business.builder().name("Сарнай").welcomeMessage("  ").build()).contains("Сарнай"));
    }

    @Test
    void orderConfirmationCarriesTheShopsDeliveryNote() {
        Product product = product(1L, "Red shoes", null);
        Conversation conversation = conversationIn(Conversation.State.COLLECT_ADDRESS, null, product);
        conversation.getBusiness().setDeliveryNote("УБ дотор 1–2 хоногт, 5,000₮");
        conversation.setCollectedName("Bat");
        conversation.setCollectedPhone("99112233");
        when(oAuthService.getDecryptedAccessToken(conversation.getBusiness())).thenReturn("token");
        when(orderService.createOrder(any(), any(), any(), anyInt(), any(), any(), any(), any())).thenReturn(Order.builder()
                .id(44L).productName("Red shoes").quantity(1).unitPrice(BigDecimal.TEN).totalAmount(BigDecimal.TEN).build());

        chatbotEngineService.process(conversation, "БЗД", "INSTAGRAM");

        verify(metaReplyService).sendText(eq("customer-1"), contains("🚚 УБ дотор 1–2 хоногт, 5,000₮"), eq("token"));
    }

    // ─── Phone numbers ───────────────────────────────────────────────────────

    @Test
    void phoneNumbersAreAcceptedInTheUsualFormats() {
        for (String input : List.of("99112233", " 9911 2233 ", "9911-2233", "+976 9911 2233", "+97699112233", "97699112233", "(9911) 2233")) {
            assertEquals("99112233", ChatbotEngineService.normalizePhone(input), "input: " + input);
        }
        for (String input : List.of("9911223", "991122334", "утас", "", "+1 9911 2233")) {
            assertNull(ChatbotEngineService.normalizePhone(input), "input: " + input);
        }
        assertNull(ChatbotEngineService.normalizePhone(null));
    }

    @Test
    void phoneStepStoresTheBareDigits() {
        Conversation conversation = conversationIn(Conversation.State.COLLECT_PHONE, null, product(1L, "Red shoes", null));
        when(oAuthService.getDecryptedAccessToken(conversation.getBusiness())).thenReturn("token");

        chatbotEngineService.process(conversation, "+976 9911-2233", "INSTAGRAM");

        assertEquals("99112233", conversation.getCollectedPhone());
        assertEquals(Conversation.State.COLLECT_ADDRESS, conversation.getState());
    }

    @Test
    void deliveredRepliesAreRecordedInTranscript() {
        Category category = Category.builder().id(10L).name("Shoes").isActive(true).build();
        Conversation conversation = conversationIn(Conversation.State.IDLE, null, null);
        when(oAuthService.getDecryptedAccessToken(conversation.getBusiness())).thenReturn("token");
        when(categoryService.getActiveCategories(1L)).thenReturn(List.of(category));
        when(metaReplyService.sendMenuMessage(anyString(), anyString(), anyList(), anyString()))
                .thenReturn("m_out_1");

        chatbotEngineService.process(conversation, "hi", "INSTAGRAM");

        verify(messageLogService).recordOutbound(eq(conversation), eq("m_out_1"), contains("1. Shoes"));
    }
}
