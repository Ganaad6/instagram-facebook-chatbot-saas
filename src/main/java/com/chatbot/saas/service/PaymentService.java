package com.chatbot.saas.service;

import com.chatbot.saas.dto.response.OrderResponse;
import com.chatbot.saas.entity.Business;
import com.chatbot.saas.entity.Customer;
import com.chatbot.saas.entity.Order;
import com.chatbot.saas.exception.OrderNotFoundException;
import com.chatbot.saas.exception.QPayException;
import com.chatbot.saas.exception.ValidationException;
import com.chatbot.saas.repository.BusinessRepository;
import com.chatbot.saas.repository.ConversationRepository;
import com.chatbot.saas.repository.OrderRepository;
import com.chatbot.saas.service.QPayClient.Credentials;
import com.chatbot.saas.service.QPayClient.Invoice;
import com.chatbot.saas.service.QPayClient.PaymentCheck;
import com.chatbot.saas.util.EncryptionUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

/**
 * QPay payments for chat orders. Each shop connects its own QPay merchant account; when an
 * order is placed the bot sends the customer a QPay invoice link. A payment is recorded only
 * after asking QPay directly (payment/check) - the callback is just a hint to go and look, and
 * a periodic reconcile catches callbacks that never arrived.
 */
@Service
@Slf4j
public class PaymentService {

    private static final int MAX_DESCRIPTION_LENGTH = 255;
    private static final int RECONCILE_BATCH_SIZE = 200;

    private final BusinessRepository businessRepository;
    private final OrderRepository orderRepository;
    private final ConversationRepository conversationRepository;
    private final QPayClient qpayClient;
    private final EncryptionUtil encryptionUtil;
    private final OAuthService oAuthService;
    private final MetaReplyService metaReplyService;
    private final MessageLogService messageLogService;
    private final OrderNotificationService orderNotificationService;
    private final TransactionTemplate transactionTemplate;

    @Value("${oauth.state-secret}")
    private String signingSecret;

    @Value("${qpay.callback-base-url}")
    private String callbackBaseUrl;

    @Value("${qpay.reconcile-window-hours:3}")
    private long reconcileWindowHours = 3;

    public PaymentService(BusinessRepository businessRepository, OrderRepository orderRepository,
                          ConversationRepository conversationRepository, QPayClient qpayClient,
                          EncryptionUtil encryptionUtil, OAuthService oAuthService,
                          MetaReplyService metaReplyService, MessageLogService messageLogService,
                          OrderNotificationService orderNotificationService,
                          PlatformTransactionManager transactionManager) {
        this.businessRepository = businessRepository;
        this.orderRepository = orderRepository;
        this.conversationRepository = conversationRepository;
        this.qpayClient = qpayClient;
        this.encryptionUtil = encryptionUtil;
        this.oAuthService = oAuthService;
        this.metaReplyService = metaReplyService;
        this.messageLogService = messageLogService;
        this.orderNotificationService = orderNotificationService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** What's needed to ask QPay about one order, read in a short transaction. */
    private record PendingInvoice(Credentials credentials, String invoiceId, BigDecimal amount) {
    }

    // ─── Shop setup ──────────────────────────────────────────────────────────

    /** Stores the shop's QPay merchant credentials after checking QPay accepts them. */
    @Transactional
    public void connectQPay(Business business, String username, String password, String invoiceCode) {
        try {
            qpayClient.verifyCredentials(username, password);
        } catch (QPayException e) {
            throw new ValidationException(e.isAuthenticationFailed()
                    ? "QPay rejected this username or password"
                    : "Could not verify the credentials with QPay: " + e.getMessage());
        }
        business.setQpayUsername(username);
        business.setQpayPassword(encryptionUtil.encrypt(password));
        business.setQpayInvoiceCode(invoiceCode);
        businessRepository.save(business);
        log.info("Business {} connected QPay merchant {}", business.getId(), username);
    }

    /** New orders stop getting invoices; already-sent invoices can still be paid and checked. */
    @Transactional
    public void disconnectQPay(Business business) {
        business.setQpayUsername(null);
        business.setQpayPassword(null);
        business.setQpayInvoiceCode(null);
        businessRepository.save(business);
        log.info("Business {} disconnected QPay", business.getId());
    }

    // ─── Invoices ────────────────────────────────────────────────────────────

    /**
     * Creates a QPay invoice for a freshly placed chat order, within the caller's transaction.
     *
     * @return the payment link to send the customer, or null if the shop has no QPay or QPay
     *         failed - the order stands either way and the shop collects payment another way
     */
    public String requestPayment(Order order) {
        Business business = order.getBusiness();
        Credentials credentials = credentials(business);
        if (credentials == null) {
            return null;
        }
        try {
            Invoice invoice = qpayClient.createInvoice(credentials, "ORDER-" + order.getId(),
                    description(order), order.getTotalAmount(), callbackUrl(order.getId()));
            order.setPaymentStatus(Order.PaymentStatus.PENDING);
            order.setQpayInvoiceId(invoice.invoiceId());
            order.setPaymentUrl(invoice.shortUrl());
            orderRepository.save(order);
            log.info("QPay invoice {} created for order {}", invoice.invoiceId(), order.getId());
            return invoice.shortUrl();
        } catch (RuntimeException e) {
            log.warn("Could not create a QPay invoice for order {} of business {}: {}",
                    order.getId(), business.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * The shop is cancelling an order: withdraw its unpaid invoice so the customer can't pay
     * it any more. If it turns out to be paid already, the payment is recorded instead (the
     * shop then owes a refund). Runs within the caller's transaction.
     */
    public void cancelPendingInvoice(Order order) {
        if (order.getPaymentStatus() != Order.PaymentStatus.PENDING || order.getQpayInvoiceId() == null) {
            return;
        }
        Credentials credentials = credentials(order.getBusiness());
        if (credentials == null) {
            return;
        }
        try {
            PaymentCheck check = qpayClient.checkPayment(credentials, order.getQpayInvoiceId());
            if (check.covers(order.getTotalAmount())) {
                recordPaid(order, check.paymentId());
                return;
            }
            qpayClient.cancelInvoice(credentials, order.getQpayInvoiceId());
            order.setPaymentStatus(Order.PaymentStatus.NOT_REQUESTED);
            orderRepository.save(order);
            log.info("QPay invoice {} cancelled with order {}", order.getQpayInvoiceId(), order.getId());
        } catch (QPayException e) {
            log.warn("Could not cancel QPay invoice for order {}: {}", order.getId(), e.getMessage());
        }
    }

    // ─── Payment confirmation ────────────────────────────────────────────────

    /** QPay's callback for an order. Unsigned or forged callbacks are ignored. */
    public void handleCallback(Long orderId, String token) {
        if (!isValidCallbackToken(orderId, token)) {
            log.warn("Ignoring QPay callback for order {} with an invalid token", orderId);
            return;
        }
        try {
            refreshPayment(orderId);
        } catch (QPayException e) {
            // The reconcile will look again
            log.warn("QPay callback for order {}: payment check failed: {}", orderId, e.getMessage());
        }
    }

    /** A shop asks to re-check an order's payment now. */
    public OrderResponse checkPayment(Long businessId, Long orderId) {
        orderRepository.findByIdAndBusinessId(orderId, businessId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        try {
            refreshPayment(orderId);
        } catch (QPayException e) {
            throw new ValidationException("Could not check the payment with QPay: " + e.getMessage());
        }
        return transactionTemplate.execute(status -> OrderResponse.from(orderRepository.findById(orderId).orElseThrow()));
    }

    /** Catches payments whose callback never arrived (e.g. the app was down). */
    @Scheduled(fixedDelayString = "${qpay.reconcile-interval-ms:300000}",
            initialDelayString = "${qpay.reconcile-initial-delay-ms:60000}")
    public void reconcilePendingPayments() {
        List<Long> orderIds = orderRepository.findIdsByPaymentStatusCreatedAfter(Order.PaymentStatus.PENDING,
                LocalDateTime.now().minusHours(reconcileWindowHours), PageRequest.of(0, RECONCILE_BATCH_SIZE));
        for (Long orderId : orderIds) {
            try {
                refreshPayment(orderId);
            } catch (RuntimeException e) {
                log.warn("Reconcile: payment check for order {} failed: {}", orderId, e.getMessage());
            }
        }
    }

    /**
     * Asks QPay whether the order's invoice is paid and records it if so. The QPay call runs
     * outside any transaction so no database lock is held while waiting on it.
     */
    void refreshPayment(Long orderId) {
        PendingInvoice pending = transactionTemplate.execute(status -> orderRepository.findById(orderId)
                .filter(o -> o.getPaymentStatus() == Order.PaymentStatus.PENDING && o.getQpayInvoiceId() != null)
                .map(o -> {
                    Credentials credentials = credentials(o.getBusiness());
                    return credentials == null ? null
                            : new PendingInvoice(credentials, o.getQpayInvoiceId(), o.getTotalAmount());
                })
                .orElse(null));
        if (pending == null) {
            return;
        }
        PaymentCheck check = qpayClient.checkPayment(pending.credentials(), pending.invoiceId());
        if (!check.covers(pending.amount())) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow();
            if (order.getPaymentStatus() == Order.PaymentStatus.PENDING) {
                recordPaid(order, check.paymentId());
            }
        });
    }

    private void recordPaid(Order order, String paymentId) {
        order.setPaymentStatus(Order.PaymentStatus.PAID);
        order.setQpayPaymentId(paymentId);
        order.setPaidAt(LocalDateTime.now());
        orderRepository.save(order);
        log.info("Order {} paid via QPay (payment {})", order.getId(), paymentId);

        Business business = order.getBusiness();
        tellCustomer(order, String.format("✅ Таны #%d захиалгын төлбөр %s амжилттай төлөгдлөө. Баярлалаа! 🙏",
                order.getId(), ChatbotEngineService.formatPrice(order.getTotalAmount())));
        orderNotificationService.notifyPaymentReceived(business.getNotificationWebhookUrl(), business.getId(),
                order.getId(), order.getTotalAmount(), paymentId);
    }

    private void tellCustomer(Order order, String text) {
        try {
            String token = oAuthService.getDecryptedAccessToken(order.getBusiness());
            Customer customer = order.getCustomer();
            String recipientId = customer.getPlatformUserId(order.getPlatform().name());
            if (token == null || recipientId == null) {
                return;
            }
            String messageId = metaReplyService.sendText(recipientId, text, token);
            conversationRepository.findFirstByOrderId(order.getId())
                    .ifPresent(conversation -> messageLogService.recordOutbound(conversation, messageId, text));
        } catch (RuntimeException e) {
            log.warn("Could not send the payment confirmation for order {}: {}", order.getId(), e.getMessage());
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Credentials credentials(Business business) {
        if (!business.isQpayConnected()) {
            return null;
        }
        return new Credentials(business.getQpayUsername(), encryptionUtil.decrypt(business.getQpayPassword()),
                business.getQpayInvoiceCode());
    }

    private static String description(Order order) {
        String text = String.format("Захиалга #%d: %s × %d", order.getId(), order.getProductName(), order.getQuantity());
        return text.length() > MAX_DESCRIPTION_LENGTH ? text.substring(0, MAX_DESCRIPTION_LENGTH) : text;
    }

    String callbackUrl(Long orderId) {
        String base = StringUtils.trimTrailingCharacter(callbackBaseUrl, '/');
        return base + "/webhook/qpay/" + orderId + "?token=" + callbackToken(orderId);
    }

    /**
     * Only QPay (which got the URL from us) knows an order's callback token, so random callers
     * can't make us poll QPay for arbitrary orders.
     */
    private String callbackToken(Long orderId) {
        return new HmacUtils(HmacAlgorithms.HMAC_SHA_256, signingSecret).hmacHex("qpay-callback:" + orderId);
    }

    private boolean isValidCallbackToken(Long orderId, String token) {
        return token != null && MessageDigest.isEqual(
                callbackToken(orderId).getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }
}
