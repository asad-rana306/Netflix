package com.Netfilx.Catalog.Config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogDatabaseSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final boolean ENABLE_SEEDER = true;
    private static final int BATCH_SIZE = 5_000;

    // We maintain the 10x multiplier to ensure the database hits 50,000+ records for load testing
    private static final int MULTIPLIER = 10;

    private static final String[] DEFAULT_GENRES = {
            "Action", "Sci-Fi", "Comedy", "Drama", "Horror", "Romance",
            "Thriller", "Documentary", "Animation", "Crime", "Fantasy", "Mystery", "Adventure", "Family"
    };

    @Override
    public void run(String... args) throws Exception {
        if (!ENABLE_SEEDER) {
            log.info("CatalogDatabaseSeeder is disabled.");
            return;
        }

        Integer currentTitleCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM titles", Integer.class);
        if (currentTitleCount != null && currentTitleCount > 0) {
            log.info("Catalog database already contains {} titles. Skipping TMDB import.", currentTitleCount);
            return;
        }

        log.info("=========================================================");
        log.info("🚀 IMPORTING TMDB MOVIES DATASET (Multiplied x10 -> 50,000 Records)");
        log.info("=========================================================");

        long startTime = System.currentTimeMillis();

        // 1. Seed base genres and retrieve lookup map
        Map<String, Long> genreMap = seedAndGetGenres();

        // 2. Load CSV and bulk insert x10 (Extreme Payload completely removed)
        seedFromTmdbCsv(genreMap);

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("=========================================================");
        log.info("✅ SUCCESS! Imported {} total records in {} ms ({:.2f} seconds)",
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM titles", Integer.class),
                totalTime, totalTime / 1000.0);
        log.info("=========================================================");
    }

    private Map<String, Long> seedAndGetGenres() {
        String insertSql = "INSERT INTO genres (name) VALUES (?) ON CONFLICT (name) DO NOTHING";
        List<Object[]> genreBatch = new ArrayList<>();

        for (String g : DEFAULT_GENRES) {
            genreBatch.add(new Object[]{ g });
        }
        jdbcTemplate.batchUpdate(insertSql, genreBatch);

        Map<String, Long> map = new HashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id, name FROM genres");
        for (Map<String, Object> row : rows) {
            map.put(((String) row.get("name")).toLowerCase(), ((Number) row.get("id")).longValue());
        }
        return map;
    }

    private void seedFromTmdbCsv(Map<String, Long> genreMap) {
        // 🔥 NOTICE: Removed fat_payload from the INSERT statement
        String sqlTitles = """
            INSERT INTO titles (
                id, title, description, type, maturity_rating, 
                thumbnail_url, hls_master_url, preview_url, release_year, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
        """;

        String sqlTitleGenres = "INSERT INTO title_genres (title_id, genre_id) VALUES (?, ?)";

        List<String> rawCsvLines = new ArrayList<>();

        try {
            ClassPathResource resource = new ClassPathResource("data/movies.csv");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                boolean isHeader = true;
                while ((line = reader.readLine()) != null) {
                    if (isHeader) {
                        isHeader = false;
                        continue;
                    }
                    if (!line.isBlank()) {
                        rawCsvLines.add(line);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to read src/main/resources/data/movies.csv.", e);
            return;
        }

        log.info("Read {} unique movie rows from CSV. Duplicating across {} passes...", rawCsvLines.size(), MULTIPLIER);

        List<Object[]> titleBatch = new ArrayList<>();
        List<Object[]> genreBatch = new ArrayList<>();
        int totalInserted = 0;

        for (int pass = 1; pass <= MULTIPLIER; pass++) {
            for (String line : rawCsvLines) {
                // Regex splits strictly on commas outside of quotes
                String[] columns = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                if (columns.length < 18) continue;

                try {
                    UUID titleId = UUID.randomUUID();

                    // TMDB Mapping based on your exact CSV layout
                    String rawGenres = columns[1].replaceAll("^\"|\"$", "");
                    String overview = columns[7].replaceAll("^\"|\"$", "").trim();
                    String releaseDate = columns[11].replaceAll("^\"|\"$", "").trim();
                    String rawTitleName = columns[17].replaceAll("^\"|\"$", "").trim();

                    if (rawTitleName.isBlank()) continue;
                    if (overview.isBlank()) overview = "An immersive streaming title: " + rawTitleName;

                    String titleName = (pass == 1) ? rawTitleName : rawTitleName + " Vol. " + pass;

                    int releaseYear = 2015;
                    if (releaseDate.length() >= 4) {
                        try {
                            releaseYear = Integer.parseInt(releaseDate.substring(0, 4));
                        } catch (NumberFormatException ignored) {}
                    }

                    boolean isMovie = (totalInserted % 4 != 0); // Mix of Movies/Series
                    String type = isMovie ? "MOVIE" : "SERIES";
                    String rating = (totalInserted % 5 == 0) ? "PG-13" : "TV-MA";
                    String thumbnailUrl = "https://picsum.photos/seed/" + titleId + "/300/160";

                    // Object array strictly matches the 9 ? parameters in the SQL
                    titleBatch.add(new Object[]{
                            titleId, titleName, overview, type, rating,
                            thumbnailUrl, null, null, releaseYear
                    });

                    // Parse the TMDB JSON Genre format safely
                    if (!rawGenres.isBlank() && rawGenres.startsWith("[")) {
                        try {
                            // Replaces escaped double-quotes ("") with standard quotes to prevent Jackson parsing errors
                            String cleanJson = rawGenres.replace("\"\"", "\"");
                            JsonNode genresNode = objectMapper.readTree(cleanJson);

                            for (JsonNode gNode : genresNode) {
                                String genreName = gNode.path("name").asText().toLowerCase();
                                Long gId = genreMap.get(genreName);

                                if (gId == null && !genreName.isBlank()) {
                                    jdbcTemplate.update("INSERT INTO genres (name) VALUES (?) ON CONFLICT DO NOTHING", gNode.path("name").asText());
                                    gId = jdbcTemplate.queryForObject("SELECT id FROM genres WHERE LOWER(name) = ?", Long.class, genreName);
                                    if (gId != null) genreMap.put(genreName, gId);
                                }

                                if (gId != null) {
                                    genreBatch.add(new Object[]{ titleId, gId });
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    totalInserted++;

                    // Flush batch periodically to prevent memory exhaustion
                    if (titleBatch.size() >= BATCH_SIZE) {
                        jdbcTemplate.batchUpdate(sqlTitles, titleBatch);
                        if (!genreBatch.isEmpty()) jdbcTemplate.batchUpdate(sqlTitleGenres, genreBatch);

                        log.info(" -> Seeded {} / 50,000 records...", totalInserted);
                        titleBatch.clear();
                        genreBatch.clear();
                    }
                } catch (Exception e) {
                    // Skip malformed rows
                }
            }
        }

        // Flush any remaining rows
        if (!titleBatch.isEmpty()) {
            jdbcTemplate.batchUpdate(sqlTitles, titleBatch);
            if (!genreBatch.isEmpty()) jdbcTemplate.batchUpdate(sqlTitleGenres, genreBatch);
            log.info(" -> Final batch flushed. Total seeded: {}", totalInserted);
        }
    }
}