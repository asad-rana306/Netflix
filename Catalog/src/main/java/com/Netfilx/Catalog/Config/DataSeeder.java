package com.Netfilx.Catalog.Config;

import com.Netfilx.Catalog.Entity.Episode;
import com.Netfilx.Catalog.Entity.Genre;
import com.Netfilx.Catalog.Entity.Title;
import com.Netfilx.Catalog.Repository.GenreRepository;
import com.Netfilx.Catalog.Repository.TitleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final GenreRepository genreRepository;
    private final TitleRepository titleRepository;

    // Public test HLS streams for video playback testing
    private static final String SAMPLE_HLS_1 = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8";
    private static final String SAMPLE_HLS_2 = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8";

    @Override
    @Transactional
    public void run(String... args) {
        if (titleRepository.count() > 0) {
            log.info("Catalog database already seeded. Skipping initialization.");
            return;
        }

        log.info("Seeding initial Netflix Catalog genres and titles...");

        // 1. Create Genres
        Genre action = genreRepository.save(Genre.builder().name("Action").build());
        Genre sciFi = genreRepository.save(Genre.builder().name("Sci-Fi").build());
        Genre drama = genreRepository.save(Genre.builder().name("Drama").build());
        Genre comedy = genreRepository.save(Genre.builder().name("Comedy").build());
        Genre animation = genreRepository.save(Genre.builder().name("Animation").build());

        // 2. Create Sample Movies
        Title tearsOfSteel = Title.builder()
                .title("Tears of Steel")
                .description("In a dystopian future, a group of soldiers and scientists gather in Amsterdam to stage a desperate counter-attack against rogue machinery.")
                .type("MOVIE")
                .maturityRating("TV-14")
                .releaseYear(2024)
                .thumbnailUrl("https://images.unsplash.com/photo-1534447677768-be436bb09401?w=800&auto=format&fit=crop&q=80")
                .hlsMasterUrl(SAMPLE_HLS_2)
                .genres(Set.of(action, sciFi))
                .build();

        Title cyberPulse = Title.builder()
                .title("Cyber Pulse")
                .description("A rogue hacker discovers an encrypted sub-network that controls the metropolis power grid before the system fights back.")
                .type("MOVIE")
                .maturityRating("TV-MA")
                .releaseYear(2025)
                .thumbnailUrl("https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&auto=format&fit=crop&q=80")
                .hlsMasterUrl(SAMPLE_HLS_1)
                .genres(Set.of(sciFi, action))
                .build();

        Title cosmicVoyage = Title.builder()
                .title("Cosmic Voyage")
                .description("An animated expedition across deep space uncovers a forgotten civilization at the edge of the galaxy.")
                .type("MOVIE")
                .maturityRating("PG-13")
                .releaseYear(2026)
                .thumbnailUrl("https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=800&auto=format&fit=crop&q=80")
                .hlsMasterUrl(SAMPLE_HLS_1)
                .genres(Set.of(animation, sciFi))
                .build();

        // 3. Create Sample TV Series with Episodes
        Title strangerRealms = Title.builder()
                .title("Stranger Realms")
                .description("When a young boy vanishes from a small town, a secret laboratory and alternate dimensions unravel a dark conspiracy.")
                .type("SERIES")
                .maturityRating("TV-MA")
                .releaseYear(2025)
                .thumbnailUrl("https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=800&auto=format&fit=crop&q=80")
                .genres(Set.of(drama, sciFi))
                .build();

        // Add Season 1 Episodes for Stranger Realms
        Episode ep1 = Episode.builder()
                .title(strangerRealms)
                .episodeTitle("Chapter One: The Vanishing")
                .seasonNumber(1)
                .episodeNumber(1)
                .hlsMasterUrl(SAMPLE_HLS_1)
                .durationSeconds(2850)
                .build();

        Episode ep2 = Episode.builder()
                .title(strangerRealms)
                .episodeTitle("Chapter Two: The Signal")
                .seasonNumber(1)
                .episodeNumber(2)
                .hlsMasterUrl(SAMPLE_HLS_2)
                .durationSeconds(3100)
                .build();

        strangerRealms.setEpisodes(List.of(ep1, ep2));

        // Save All Titles
        titleRepository.saveAll(List.of(tearsOfSteel, cyberPulse, cosmicVoyage, strangerRealms));

        log.info("Catalog database seeded successfully with {} titles!", titleRepository.count());
    }
}