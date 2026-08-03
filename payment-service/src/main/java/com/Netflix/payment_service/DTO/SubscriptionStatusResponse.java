package com.Netflix.payment_service.DTO;

public record SubscriptionStatusResponse(
        boolean active,
        String planTier,
        String status,
        String currentPeriodEnd
) {}