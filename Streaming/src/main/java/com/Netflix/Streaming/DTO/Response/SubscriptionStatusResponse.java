package com.Netflix.Streaming.DTO.Response;


public record SubscriptionStatusResponse(
        boolean active,
        String planTier,
        String status,
        String currentPeriodEnd
) {}