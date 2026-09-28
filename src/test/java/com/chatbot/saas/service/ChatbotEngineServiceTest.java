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

    private ChatbotEngineService chatbotEngineService;

    private static final String DIRECTUS_PUBLIC_URL = "http://localhost:8055";

    @BeforeEach
    void setUp() {
        chatbotEngineService = new ChatbotEngineService(
                conversationRepository, categoryService, productService,
                orderService, orderNotificationService, metaReplyService, oAuthService, messageLogService);
        setDirectusPublicUrl(chatbotEngineService, DIRECTUS_PUBLIC_URL);
    }

    private static void setDirectusPublicUrl(ChatbotEngineService service, String url) {
        try {
            var field = ChatbotEngineService.class.getDeclaredField("directusPublicUrl");
            field.setAccessible(true);
            field.set(service, url);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
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

    @Test
    void showProductsSendsImageOnlyForProductsThatHaveOne() {
        Business business = Business.builder().id(1L).build();
        Customer customer = Customer.builder().instagramUserId("customer-1").build();
        Category category = Category.builder().id(10L).name("Shoes").build();
        Conversation conversation = Conversation.builder()
                .business(business)
                .customer(customer)
                .status(Conversation.Status.ACTIVE)
                .state(Conversation.State.AWAITING_CATEGORY)
                .build();

        UUID imageFileId = UUID.randomUUID();
        Product withImage = product(1L, "Red shoes", imageFileId);
        Product withoutImage = product(2L, "Blue shoes", null);

        when(oAuthService.getDecryptedAccessToken(business)).thenReturn("token");
        when(categoryService.getActiveCategories(1L)).thenReturn(List.of(category));
        when(productService.getActiveProductsByCategory(1L, 10L))
                .thenReturn(List.of(withImage, withoutImage));

        chatbotEngineService.process(conversation, "1", "INSTAGRAM");

        verify(metaReplyService, times(1))
                .sendImage("customer-1", DIRECTUS_PUBLIC_URL + "/assets/" + imageFileId, "token");
        verify(metaReplyService, times(1))
                .sendMenuMessage(eq("customer-1"), eq("INSTAGRAM"), anyString(), anyList(), eq("token"));
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
        verify(metaReplyService).sendMenuMessage(eq("customer-1"), eq("INSTAGRAM"), anyString(), anyList(), eq("token"));
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

        verify(metaReplyService).sendMenuMessage(eq("psid-1"), eq("FACEBOOK"), anyString(), anyList(), eq("token"));
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
        verify(metaReplyService).sendText(eq("customer-1"), contains("Хэдэн ширхэг"), eq("token"));
    }

    @Test
    void validQuantityShowsConfirmationWithTotal() {
        Conversation conversation = conversationIn(Conversation.State.AWAITING_QUANTITY, null, product(1L, "Red shoes", null));
        when(oAuthService.getDecryptedAccessToken(conversation.getBusiness())).thenReturn("token");

        chatbotEngineService.process(conversation, "3", "INSTAGRAM");

        assertEquals(Conversation.State.AWAITING_CONFIRMATION, conversation.getState());
        assertEquals(3, conversation.getSelectedQuantity());
        verify(metaReplyService).sendText(eq("customer-1"), contains("× 3 — нийт ₮30"), eq("token"));
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
        verify(orderNotificationService).notifyNewOrder(conversation.getBusiness(), order);
    }

    @Test
    void deliveredRepliesAreRecordedInTranscript() {
        Category category = Category.builder().id(10L).name("Shoes").isActive(true).build();
        Conversation conversation = conversationIn(Conversation.State.IDLE, null, null);
        when(oAuthService.getDecryptedAccessToken(conversation.getBusiness())).thenReturn("token");
        when(categoryService.getActiveCategories(1L)).thenReturn(List.of(category));
        when(metaReplyService.sendMenuMessage(anyString(), anyString(), anyString(), anyList(), anyString()))
                .thenReturn("m_out_1");

        chatbotEngineService.process(conversation, "hi", "INSTAGRAM");

        verify(messageLogService).recordOutbound(eq(conversation), eq("m_out_1"), contains("1. Shoes"));
    }
}
