package com.Netfilx.Catalog.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "episodes")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Episode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "title_id", nullable = false)
    private Title title;

    @Column(nullable = false)
    private String episodeTitle;

    @Column(nullable = false)
    private Integer seasonNumber;

    @Column(nullable = false)
    private Integer episodeNumber;

    // HLS video stream link for this specific episode
    private String hlsMasterUrl;

    private Integer durationSeconds;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}