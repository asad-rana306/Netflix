package com.Netfilx.User.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String profileName;

    private String avatarUrl;

    @Builder.Default
    private Boolean isKids = false;

    @Builder.Default
    private String maturityRating = "TV-MA";

    // Optional 4-digit PIN for profile lock (null if no PIN)
    @Column(length = 255)
    private String pin;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}