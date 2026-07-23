package com.Netflix.Streaming.Entity;


import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "watch_history",
        indexes = {
                @Index(name = "idx_profile_title", columnList = "profile_id, title_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WatchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID profileId;

    @Column(nullable = false)
    private Long titleId;

    private Long episodeId; // Null for movies

    @Column(nullable = false)
    private Long progressSeconds;

    @Column(nullable = false)
    private Long durationSeconds;

    @Column(nullable = false)
    private boolean isCompleted;

    @Column(nullable = false)
    private LocalDateTime lastWatchedAt;

    @PrePersist
    @PreUpdate
    public void onSave() {
        this.lastWatchedAt = LocalDateTime.now();
        // Mark auto-completed if user watched more than 90%
        if (this.durationSeconds > 0 && ((double) this.progressSeconds / this.durationSeconds) >= 0.90) {
            this.isCompleted = true;
        }
    }
}
