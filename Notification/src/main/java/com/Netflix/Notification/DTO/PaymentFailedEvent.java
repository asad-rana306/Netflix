package com.Netflix.Notification.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentFailedEvent {
    private String eventId;
    private String transactionId;
    private String userId;
    private String userEmail;
    private String userName;
    private BigDecimal amount;
    private String currency;
    private String failureReason; // e.g., "INSUFFICIENT_FUNDS", "CARD_EXPIRED"
    private String retryPaymentUrl;
    private Instant attemptedAt;
}