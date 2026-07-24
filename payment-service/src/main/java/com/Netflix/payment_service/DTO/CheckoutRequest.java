package com.Netflix.payment_service.DTO;


public record CheckoutRequest(
        String priceId,   // Stripe Price ID (e.g., price_1N...)
        String planTier   // "STANDARD" or "PREMIUM"
) {}