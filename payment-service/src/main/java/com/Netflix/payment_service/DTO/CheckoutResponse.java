package com.Netflix.payment_service.DTO;

public record CheckoutResponse(
        String checkoutUrl,
        String sessionId
) {}