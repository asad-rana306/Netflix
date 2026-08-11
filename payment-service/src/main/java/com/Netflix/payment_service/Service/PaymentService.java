package com.Netflix.payment_service.Service;

import com.Netflix.payment_service.client.UserServiceClient;
import com.Netflix.payment_service.DTO.CheckoutRequest;
import com.Netflix.payment_service.DTO.CheckoutResponse;
import com.Netflix.payment_service.DTO.PaymentEvent;
import com.Netflix.payment_service.DTO.SubscriptionStatusResponse;
import com.Netflix.payment_service.DTO.UpdateSubscriptionRequest;
import com.Netflix.payment_service.Entity.Subscription;
import com.Netflix.payment_service.Repository.SubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final SubscriptionRepository subscriptionRepository;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final UserServiceClient userServiceClient;

    @Value("${stripe.api-key}")
    private String stripeApiKey;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    @Value("${stripe.prices.standard:}")
    private String standardPriceId;

    @Value("${stripe.prices.premium:}")
    private String premiumPriceId;

    private static final String TOPIC_PAYMENT_EVENTS = "payment-events";
    private static final String REDIS_SUB_KEY_PREFIX = "user:";

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    /**
     * Creates a Stripe Checkout Session. Prevents execution if user already holds an ACTIVE subscription.
     */
    public CheckoutResponse createCheckoutSession(String userId, String userEmail, CheckoutRequest request) throws StripeException {
        // 🔒 Guard Clause: Block checkout if active subscription exists
        boolean hasActiveSub = subscriptionRepository.findByUserId(userId)
                .map(sub -> sub.getStatus() == Subscription.SubscriptionStatus.ACTIVE)
                .orElse(false);

        if (hasActiveSub) {
            log.warn(">>> Checkout blocked: User ID {} already has an ACTIVE subscription.", userId);
            throw new IllegalStateException("You already have an active subscription.");
        }

        String resolvedPriceId = resolvePriceId(request.priceId(), request.planTier());

        SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .putMetadata("userId", userId)
                .putMetadata("userEmail", userEmail != null ? userEmail : "")
                .putMetadata("planTier", request.planTier() != null ? request.planTier() : "PREMIUM")
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPrice(resolvedPriceId)
                                .setQuantity(1L)
                                .build()
                );

        if (userEmail != null && !userEmail.isBlank()) {
            paramsBuilder.setCustomerEmail(userEmail);
        }

        Session session = Session.create(paramsBuilder.build());
        log.info(">>> Created Checkout Session ID: {} for userId: {} (Price: {})", session.getId(), userId, resolvedPriceId);
        return new CheckoutResponse(session.getUrl(), session.getId());
    }

    /**
     * Cancels subscription in Stripe, updates payment-service DB, evicts Redis cache, and syncs user-service via OpenFeign.
     */
    @Transactional
    public void cancelSubscription(String userId) throws StripeException {
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("No subscription record found for user ID: " + userId));

        if (subscription.getStatus() != Subscription.SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("Subscription is not active and cannot be canceled.");
        }

        // 1. Cancel active subscription in Stripe
        if (subscription.getStripeSubscriptionId() != null && !subscription.getStripeSubscriptionId().startsWith("sub_test_dummy")) {
            com.stripe.model.Subscription stripeSub = com.stripe.model.Subscription.retrieve(subscription.getStripeSubscriptionId());
            stripeSub.cancel();
            log.info(">>> Successfully canceled Stripe subscription ID: {}", subscription.getStripeSubscriptionId());
        }

        // 2. Update payment-service local PostgreSQL DB
        subscription.setStatus(Subscription.SubscriptionStatus.CANCELED);
        subscription.setUpdatedAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);

        // 3. Evict Redis cache
        try {
            redisTemplate.delete(REDIS_SUB_KEY_PREFIX + userId + ":subscription");
        } catch (Exception e) {
            log.warn(">>> Failed to clear Redis subscription cache for user {}: {}", userId, e.getMessage());
        }

        // 4. Synchronously update user-service state to INACTIVE via OpenFeign
        syncSubscriptionToUserService(userId, subscription.getStripeCustomerId(), "INACTIVE", subscription.getPlanTier());

        // 5. Emit background Kafka event
        try {
            publishPaymentEvent(userId, null, subscription.getStripeCustomerId(), "CANCELED", subscription.getPlanTier(), "SUBSCRIPTION_CANCELED");
        } catch (Exception e) {
            log.warn(">>> Kafka event dispatch skipped: {}", e.getMessage());
        }
    }

    @Transactional
    public void handleStripeWebhook(String rawPayload, String sigHeader) throws SignatureVerificationException {
        Event event = Webhook.constructEvent(rawPayload, sigHeader, webhookSecret);
        log.info(">>> Webhook Event Received: {}", event.getType());

        try {
            switch (event.getType()) {
                case "checkout.session.completed" -> {
                    Session session = deserializeEventObject(event, Session.class);
                    if (session != null) {
                        processCheckoutSuccess(session);
                    } else {
                        log.error(">>> Failed to deserialize Session object from event!");
                    }
                }
                case "invoice.payment_succeeded" -> {
                    Invoice invoice = deserializeEventObject(event, Invoice.class);
                    if (invoice != null) processInvoicePaymentSucceeded(invoice);
                }
                case "invoice.payment_failed" -> {
                    Invoice invoice = deserializeEventObject(event, Invoice.class);
                    if (invoice != null) processInvoicePaymentFailed(invoice);
                }
                case "customer.subscription.deleted" -> {
                    com.stripe.model.Subscription sub = deserializeEventObject(event, com.stripe.model.Subscription.class);
                    if (sub != null) processSubscriptionCanceled(sub);
                }
                default -> log.info(">>> Unhandled event type ignored: {}", event.getType());
            }
        } catch (Exception e) {
            log.error(">>> Error processing webhook event {}: {}", event.getType(), e.getMessage(), e);
        }
    }

    private void processCheckoutSuccess(Session session) {
        log.info(">>> Inside processCheckoutSuccess for Session ID: {}", session.getId());

        String userId = null;
        String userEmail = null;
        String planTier = "PREMIUM";

        if (session != null && session.getMetadata() != null) {
            userId = session.getMetadata().get("userId");
            userEmail = session.getMetadata().get("userEmail");
            if (session.getMetadata().get("planTier") != null) {
                planTier = session.getMetadata().get("planTier");
            }
        }

        if (userEmail == null || userEmail.isBlank()) {
            userEmail = session != null ? session.getCustomerEmail() : null;
        }

        if (userId == null || userId.isBlank()) {
            userId = "3"; // Fallback test user ID for direct CLI triggers
            log.warn(">>> Metadata userId was empty! Using fallback userId: {}", userId);
        }

        String stripeCustomerId = (session != null && session.getCustomer() != null)
                ? session.getCustomer() : "cus_test_dummy";

        String stripeSubscriptionId = (session != null && session.getSubscription() != null)
                ? session.getSubscription() : "sub_test_dummy";

        log.info(">>> Persisting Subscription -> userId: {}, customerId: {}", userId, stripeCustomerId);

        final String finalUserId = userId;
        Subscription subscription = subscriptionRepository.findByUserId(finalUserId)
                .orElseGet(() -> Subscription.builder()
                        .userId(finalUserId)
                        .createdAt(LocalDateTime.now())
                        .build());

        subscription.setStripeCustomerId(stripeCustomerId);
        subscription.setStripeSubscriptionId(stripeSubscriptionId);
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        subscription.setPlanTier(planTier);
        subscription.setUpdatedAt(LocalDateTime.now());

        Subscription saved = subscriptionRepository.save(subscription);
        log.info(">>> SUCCESS! Saved Subscription ID: {} to PostgreSQL!", saved.getId());

        try {
            cacheSubscriptionInRedis(finalUserId, "ACTIVE_" + planTier);
        } catch (Exception e) {
            log.warn(">>> Redis skipped: {}", e.getMessage());
        }

        // Synchronously update user-service via OpenFeign
        syncSubscriptionToUserService(finalUserId, stripeCustomerId, "ACTIVE", planTier);

        try {
            publishPaymentEvent(finalUserId, userEmail, stripeCustomerId, "ACTIVE", planTier, "PAYMENT_SUCCEEDED");
        } catch (Exception e) {
            log.warn(">>> Kafka skipped: {}", e.getMessage());
        }
    }

    private void processInvoicePaymentSucceeded(Invoice invoice) {
        String stripeCustomerId = invoice.getCustomer();
        subscriptionRepository.findByStripeCustomerId(stripeCustomerId).ifPresent(sub -> {
            sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
            sub.setUpdatedAt(LocalDateTime.now());
            subscriptionRepository.save(sub);
            log.info(">>> Updated subscription to ACTIVE for customer: {}", stripeCustomerId);

            // Synchronously update user-service via OpenFeign
            syncSubscriptionToUserService(sub.getUserId(), stripeCustomerId, "ACTIVE", sub.getPlanTier());

            try {
                publishPaymentEvent(sub.getUserId(), invoice.getCustomerEmail(), stripeCustomerId, "ACTIVE", sub.getPlanTier(), "INVOICE_PAYMENT_SUCCEEDED");
            } catch (Exception ignored) {}
        });
    }

    private void processInvoicePaymentFailed(Invoice invoice) {
        String stripeCustomerId = invoice.getCustomer();
        subscriptionRepository.findByStripeCustomerId(stripeCustomerId).ifPresent(sub -> {
            sub.setStatus(Subscription.SubscriptionStatus.PAST_DUE);
            sub.setUpdatedAt(LocalDateTime.now());
            subscriptionRepository.save(sub);
            log.info(">>> Updated subscription to PAST_DUE for customer: {}", stripeCustomerId);

            // Synchronously update user-service via OpenFeign
            syncSubscriptionToUserService(sub.getUserId(), stripeCustomerId, "INACTIVE", sub.getPlanTier());

            try {
                publishPaymentEvent(sub.getUserId(), invoice.getCustomerEmail(), stripeCustomerId, "PAST_DUE", sub.getPlanTier(), "PAYMENT_FAILED");
            } catch (Exception ignored) {}
        });
    }

    private void processSubscriptionCanceled(com.stripe.model.Subscription stripeSub) {
        subscriptionRepository.findByStripeSubscriptionId(stripeSub.getId()).ifPresent(sub -> {
            sub.setStatus(Subscription.SubscriptionStatus.CANCELED);
            sub.setUpdatedAt(LocalDateTime.now());
            subscriptionRepository.save(sub);
            try {
                redisTemplate.delete(REDIS_SUB_KEY_PREFIX + sub.getUserId() + ":subscription");
            } catch (Exception ignored) {}
            log.info(">>> Canceled subscription for user: {}", sub.getUserId());

            // Synchronously update user-service via OpenFeign
            syncSubscriptionToUserService(sub.getUserId(), sub.getStripeCustomerId(), "INACTIVE", sub.getPlanTier());
        });
    }

    private void cacheSubscriptionInRedis(String userId, String state) {
        String key = REDIS_SUB_KEY_PREFIX + userId + ":subscription";
        redisTemplate.opsForValue().set(key, state, 30, TimeUnit.DAYS);
    }

    private void publishPaymentEvent(String userId, String userEmail, String stripeCustomerId, String status, String planTier, String eventType) {
        PaymentEvent event = new PaymentEvent(userId, stripeCustomerId, status, planTier, eventType, Instant.now());
        kafkaTemplate.send(TOPIC_PAYMENT_EVENTS, userId, event);
    }

    /**
     * Helper to issue synchronous HTTP updates to user-service using OpenFeign.
     */
    private void syncSubscriptionToUserService(String userId, String stripeCustomerId, String status, String planTier) {
        try {
            UpdateSubscriptionRequest request = UpdateSubscriptionRequest.builder()
                    .planTier(planTier != null ? planTier : "STANDARD")
                    .status(status)
                    .stripeCustomerId(stripeCustomerId)
                    .build();

            userServiceClient.updateSubscription(userId, request);
            log.info(">>> Synchronously updated user-service subscription for userId: {}", userId);
        } catch (Exception e) {
            log.error(">>> Failed to sync subscription to user-service for userId {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Resolves raw input strings into valid Stripe Price IDs.
     */
    private String resolvePriceId(String rawPriceId, String planTier) {
        if (rawPriceId != null && rawPriceId.startsWith("price_1")) {
            return rawPriceId;
        }
        if ("PREMIUM".equalsIgnoreCase(planTier) && premiumPriceId != null && !premiumPriceId.isBlank()) {
            return premiumPriceId;
        }
        if (standardPriceId != null && !standardPriceId.isBlank()) {
            return standardPriceId;
        }
        return rawPriceId;
    }

    /**
     * Reusable helper for safe object deserialization from Stripe Webhook Event wrappers.
     */
    @SuppressWarnings("unchecked")
    private <T> T deserializeEventObject(Event event, Class<T> clazz) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        if (deserializer.getObject().isPresent() && clazz.isInstance(deserializer.getObject().get())) {
            return (T) deserializer.getObject().get();
        }
        try {
            return (T) deserializer.deserializeUnsafe();
        } catch (EventDataObjectDeserializationException e) {
            log.error(">>> Failed to deserialize {} from event: {}", clazz.getSimpleName(), e.getMessage());
            return null;
        }
    }

    public SubscriptionStatusResponse getSubscriptionStatus(String userId) {
        return subscriptionRepository.findByUserId(userId)
                .map(sub -> new SubscriptionStatusResponse(
                        sub.getStatus() == Subscription.SubscriptionStatus.ACTIVE,
                        sub.getPlanTier(),
                        sub.getStatus().name(),
                        sub.getCurrentPeriodEnd() != null ? sub.getCurrentPeriodEnd().toString() : null
                ))
                .orElse(new SubscriptionStatusResponse(false, "NONE", "INACTIVE", null));
    }
}