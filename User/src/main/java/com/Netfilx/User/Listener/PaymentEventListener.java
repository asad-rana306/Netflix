//package com.Netfilx.User.Listener;
//
//import com.Netfilx.User.DTO.Request.PaymentEvent;
//import com.Netfilx.User.Entity.Subscription;
//import com.Netfilx.User.Entity.User;
//import com.Netfilx.User.Repository.UserRepository;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.Duration;
//import java.util.Optional;
//import java.util.UUID;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class PaymentEventListener {
//
//    private static final String IDEMPOTENCY_KEY_PREFIX = "processed:payment-event:";
//    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);
//
//    private final UserRepository userRepository;
//    private final RedisTemplate<String, Object> redisTemplate;
//
//    /**
//     * Kafka Listener consuming payment events from payment-service.
//     * Implements Redis-backed Consumer Idempotency and transactional database updates.
//     */
//    @KafkaListener(topics = "payment-events", groupId = "user-service-group")
//    @Transactional
//    public void handlePaymentEvent(PaymentEvent event) {
//        // 1. Defensive Payload Validation
//        if (event == null) {
//            log.error("Kafka PaymentEvent processing failed: Received null event payload.");
//            return;
//        }
//
//        if (event.userId() == null || event.userId().isBlank()) {
//            log.error("Kafka PaymentEvent processing failed: Event userId is null or empty.");
//            return;
//        }
//
//        if (event.eventType() == null || event.eventType().isBlank()) {
//            log.error("Kafka PaymentEvent processing failed: Event type is null or empty for userId: {}", event.userId());
//            return;
//        }
//
//        // 2. Consumer Idempotency Check via Redis SETNX (Prevents duplicate message processing)
//        String idempotencyKey = generateIdempotencyKey(event);
//        Boolean isNewEvent = redisTemplate.opsForValue()
//                .setIfAbsent(idempotencyKey, "PROCESSED", IDEMPOTENCY_TTL);
//
//        if (Boolean.FALSE.equals(isNewEvent)) {
//            log.warn("Duplicate PaymentEvent ignored via Redis Idempotency Guard. Key: '{}'", idempotencyKey);
//            return;
//        }
//
//        log.info("Processing PaymentEvent [Type: {}] for userId/email identifier: {}", event.eventType(), event.userId());
//
//        try {
//            // 3. Locate User by UUID or Email
//            Optional<User> userOptional = findUserByIdOrEmail(event.userId());
//
//            if (userOptional.isEmpty()) {
//                log.warn("PaymentEvent processing skipped: User not found for identifier: {}", event.userId());
//                return;
//            }
//
//            User user = userOptional.get();
//
//            // 4. Fetch attached Subscription entity or initialize new one
//            Subscription subscription = user.getSubscription();
//            if (subscription == null) {
//                subscription = new Subscription();
//                subscription.setUser(user);
//                user.setSubscription(subscription);
//            }
//
//            // 5. Update subscription state based on event outcome
//            String eventType = event.eventType().toUpperCase();
//            if (eventType.contains("SUCCEEDED") || eventType.contains("SUCCESS") || eventType.contains("CREATED")) {
//                subscription.setPlanTier(event.planTier() != null ? event.planTier() : "STANDARD");
//                subscription.setStatus("ACTIVE");
//                if (event.stripeCustomerId() != null && !event.stripeCustomerId().isBlank()) {
//                    subscription.setStripeCustomerId(event.stripeCustomerId());
//                }
//                log.info("Subscription updated to ACTIVE [Plan: {}] for user ID: {}", subscription.getPlanTier(), user.getId());
//
//            } else if (eventType.contains("FAILED") || eventType.contains("CANCELED") || eventType.contains("DELETED")) {
//                subscription.setStatus("INACTIVE");
//                log.info("Subscription updated to INACTIVE due to event outcome [Type: {}] for user ID: {}", eventType, user.getId());
//
//            } else {
//                log.warn("Unhandled PaymentEvent type: '{}' for user ID: {}", eventType, user.getId());
//            }
//
//            // 6. Persist changes in PostgreSQL
//            userRepository.save(user);
//
//        } catch (Exception ex) {
//            // Roll back Redis idempotency key if DB transaction fails so Kafka can retry cleanly
//            redisTemplate.delete(idempotencyKey);
//            log.error("Failed to process PaymentEvent for userId: {}. Reason: {}", event.userId(), ex.getMessage(), ex);
//            throw new RuntimeException("Error processing PaymentEvent in user-service", ex);
//        }
//    }
//
//    // ==================== HELPER METHODS ====================
//
//    /**
//     * Constructs a unique Redis key combining user ID, event type, and event timestamp.
//     */
//    private String generateIdempotencyKey(PaymentEvent event) {
//        String timestampStr = event.timestamp() != null ? event.timestamp().toString() : "0";
//        return IDEMPOTENCY_KEY_PREFIX + event.userId() + ":" + event.eventType() + ":" + timestampStr;
//    }
//
//    /**
//     * Safely attempts UUID lookup first; falls back to normalized email lookup.
//     */
//    private Optional<User> findUserByIdOrEmail(String identifier) {
//        String sanitized = identifier.trim();
//        try {
//            UUID uuid = UUID.fromString(sanitized);
//            return userRepository.findById(uuid);
//        } catch (IllegalArgumentException e) {
//            return userRepository.findByEmail(sanitized.toLowerCase());
//        }
//    }
//}