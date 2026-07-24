package com.Netflix.payment_service.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String userId;

    private String stripeCustomerId;
    private String stripeSubscriptionId;

    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status; // ACTIVE, CANCELED, PAST_DUE, INACTIVE

    private String planTier; // STANDARD, PREMIUM
    private LocalDateTime currentPeriodEnd;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum SubscriptionStatus {
        ACTIVE, CANCELED, PAST_DUE, INACTIVE
    }
}