package com.Netfilx.User.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Data
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    private String planTier = "STANDARD";

    private String status = "ACTIVE";

    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
