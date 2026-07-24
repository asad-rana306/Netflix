package com.Netflix.payment_service.DTO;

import java.time.Instant;

public record PaymentEvent(
        String userId,
        String stripeCustomerId,
        String status,
        String planTier,
        String eventType, // PAYMENT_SUCCEEDED, PAYMENT_FAILED, SUBSCRIPTION_CANCELED
        Instant timestamp
) {}