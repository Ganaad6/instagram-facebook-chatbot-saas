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

    private ChatbotEngineService chatbotEngineService;

    private static final String DIRECTUS_PUBLIC_URL = "http://localhost:8055";

    @BeforeEach
    void setUp() {
        chatbotEngineService = new ChatbotEngineService(
                conversationRepository, categoryService, productService,
                orderService, orderNotificationService, metaReplyService, oAuthService);
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
}
