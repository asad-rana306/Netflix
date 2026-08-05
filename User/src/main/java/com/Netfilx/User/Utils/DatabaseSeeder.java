//package com.Netfilx.User.Utils;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.stereotype.Component;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.UUID;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class DatabaseSeeder implements CommandLineRunner {
//
//    private final JdbcTemplate jdbcTemplate;
//    private final PasswordEncoder passwordEncoder;
//
//    // Set to 'true' to run seeder on startup, 'false' once completed
//    private static final boolean ENABLE_SEEDER = true;
//
//    private static final int TOTAL_RECORDS = 100_000;
//    private static final int BATCH_SIZE = 5_000;
//
//    @Override
//    public void run(String... args) throws Exception {
//        if (!ENABLE_SEEDER) {
//            log.info("DatabaseSeeder is disabled. Skipping data seeding.");
//            return;
//        }
//
//        Integer currentUserCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
//        if (currentUserCount == null) currentUserCount = 0;
//
//        log.info("=========================================================");
//        log.info("🚀 STARTING BULK SEEDING: Current User Count = {}", currentUserCount);
//        log.info("=========================================================");
//
//        long startTime = System.currentTimeMillis();
//
//        // Generate BCrypt hash for "Password123!" using Spring Security's Encoder
//        log.info("Generating BCrypt password hash for test accounts...");
//        String defaultHashedPassword = passwordEncoder.encode("Password123!");
//
//        // 1. Insert users if missing, OR sync password hashes if users already exist
//        if (currentUserCount < TOTAL_RECORDS) {
//            seedUsers(defaultHashedPassword);
//        } else {
//            log.info("Users table already populated. Syncing password hashes to 'Password123!'...");
//            syncUserPasswords(defaultHashedPassword);
//        }
//
//        // 2. Batch Insert Subscriptions ONLY for users who don't have one yet
//        seedSubscriptions();
//
//        // 3. Batch Insert Profiles ONLY for users who don't have one yet
//        seedProfiles();
//
//        long totalTime = System.currentTimeMillis() - startTime;
//        log.info("=========================================================");
//        log.info("✅ SUCCESS! Database seeding/sync completed in {} ms ({:.2f} seconds)",
//                totalTime, totalTime / 1000.0);
//        log.info("🔑 Test Login Email    : user_98765@netflix.com (or user_1@netflix.com)");
//        log.info("🔑 Test Login Password : Password123!");
//        log.info("=========================================================");
//    }
//
//    private void seedUsers(String hashedPassword) {
//        log.info("Inserting 100,000 users into 'users' table in batches of {}...", BATCH_SIZE);
//
//        String sql = "INSERT INTO users (id, email, password_hash, status, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())";
//
//        List<Object[]> batch = new ArrayList<>();
//        for (int i = 1; i <= TOTAL_RECORDS; i++) {
//            batch.add(new Object[]{ UUID.randomUUID(), "user_" + i + "@netflix.com", hashedPassword, "ACTIVE" });
//
//            if (i % BATCH_SIZE == 0) {
//                jdbcTemplate.batchUpdate(sql, batch);
//                batch.clear();
//                log.info(" -> Seeded {} / {} users", i, TOTAL_RECORDS);
//            }
//        }
//    }
//
//    private void syncUserPasswords(String hashedPassword) {
//        // Updates all synthetic accounts to ensure their password_hash matches the current BCrypt encoder
//        String sql = "UPDATE users SET password_hash = ? WHERE email LIKE '%@netflix.com'";
//        int updatedRows = jdbcTemplate.update(sql, hashedPassword);
//        log.info(" -> Updated password hashes for {} existing synthetic users.", updatedRows);
//    }
//
//    private void seedSubscriptions() {
//        log.info("Fetching unseeded user IDs from PostgreSQL for subscription seeding...");
//
//        String unseededUsersSql = "SELECT u.id FROM users u WHERE NOT EXISTS (SELECT 1 FROM subscriptions s WHERE s.user_id = u.id)";
//        List<UUID> userIds = jdbcTemplate.queryForList(unseededUsersSql, UUID.class);
//
//        if (userIds.isEmpty()) {
//            log.info("All users already have active subscriptions. Skipping subscription seeding.");
//            return;
//        }
//
//        log.info("Batch inserting {} matching active subscriptions...", userIds.size());
//
//        String sql = "INSERT INTO subscriptions (id, user_id, plan_tier, status, stripe_customer_id, created_at) VALUES (?, ?, ?, ?, ?, NOW())";
//
//        List<Object[]> batch = new ArrayList<>();
//        int count = 0;
//
//        for (UUID userId : userIds) {
//            batch.add(new Object[]{
//                    UUID.randomUUID(),
//                    userId,
//                    "PREMIUM",
//                    "ACTIVE",
//                    "cus_test_" + userId
//            });
//            count++;
//
//            if (batch.size() == BATCH_SIZE || count == userIds.size()) {
//                jdbcTemplate.batchUpdate(sql, batch);
//                batch.clear();
//                log.info(" -> Seeded {} / {} subscriptions", count, userIds.size());
//            }
//        }
//    }
//
//    private void seedProfiles() {
//        log.info("Fetching unseeded user IDs from PostgreSQL for profile seeding...");
//
//        String unseededUsersSql = "SELECT u.id FROM users u WHERE NOT EXISTS (SELECT 1 FROM profiles p WHERE p.user_id = u.id)";
//        List<UUID> userIds = jdbcTemplate.queryForList(unseededUsersSql, UUID.class);
//
//        if (userIds.isEmpty()) {
//            log.info("All users already have profiles. Skipping profile seeding.");
//            return;
//        }
//
//        log.info("Batch inserting {} matching user profiles...", userIds.size());
//
//        String sql = "INSERT INTO profiles (id, user_id, profile_name, avatar_url, is_kids, maturity_rating, pin, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, NOW())";
//
//        List<Object[]> batch = new ArrayList<>();
//        int count = 0;
//
//        for (UUID userId : userIds) {
//            batch.add(new Object[]{
//                    UUID.randomUUID(),
//                    userId,
//                    "Main Profile",
//                    "default_avatar.png",
//                    false,
//                    "TV-MA",
//                    null
//            });
//            count++;
//
//            if (batch.size() == BATCH_SIZE || count == userIds.size()) {
//                jdbcTemplate.batchUpdate(sql, batch);
//                batch.clear();
//                log.info(" -> Seeded {} / {} profiles", count, userIds.size());
//            }
//        }
//    }
//}