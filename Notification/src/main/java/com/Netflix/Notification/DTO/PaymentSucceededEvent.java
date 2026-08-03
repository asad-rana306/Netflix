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
public class PaymentSucceededEvent {
    private String eventId;
    private String transactionId;
    private String userId;
    private String userEmail;
    private String userName;
    private BigDecimal amount;
    private String currency; // e.g., "USD"
    private String subscriptionPlan;
    private String invoicePdfUrl;
    private Instant paymentDate;
}