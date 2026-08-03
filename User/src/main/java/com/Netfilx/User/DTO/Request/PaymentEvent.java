package com.Netfilx.User.DTO.Request;

import java.time.Instant;

public record PaymentEvent(
        String userId,
        String stripeCustomerId,
        String status,
        String planTier,
        String eventType,
        Instant timestamp
) {}