package com.Netfilx.Catalog.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "titles")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Title {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 20)
    private String type; // "MOVIE" or "SERIES"

    @Builder.Default
    private String maturityRating = "TV-MA";

    private String thumbnailUrl;

    // Filled directly if type == "MOVIE"
    private String hlsMasterUrl;

    @Column(columnDefinition = "TEXT")
    private String previewUrl;

    private Integer releaseYear;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "title_genres",
            joinColumns = @JoinColumn(name = "title_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    @Builder.Default
    private Set<Genre> genres = new HashSet<>();

    @OneToMany(mappedBy = "title", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Episode> episodes = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}