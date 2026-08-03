package com.Netfilx.User.Service;

import com.Netfilx.User.Entity.RefreshToken;
import com.Netfilx.User.Entity.UserSession;
import com.Netfilx.User.Repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RedisSessionService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final long refreshTokenTtlSeconds;

    public RedisSessionService(RefreshTokenRepository refreshTokenRepository,
                               RedisTemplate<String, Object> redisTemplate,
                               @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.redisTemplate = redisTemplate;
        this.refreshTokenTtlSeconds = refreshTokenExpirationMs / 1000;
    }

    /**
     * Create and store a new Refresh Token in Redis with configured TTL.
     */
    public RefreshToken createRefreshToken(String userId, String email, String deviceId, String userAgent, String ipAddress) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID must not be null or empty when creating a refresh token.");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be null or empty when creating a refresh token.");
        }

        String token = UUID.randomUUID().toString();
        String assignedDeviceId = (deviceId != null && !deviceId.isBlank()) ? deviceId : UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .id(token)
                .userId(userId)
                .email(email)
                .deviceId(assignedDeviceId)
                .userAgent(userAgent != null ? userAgent : "Unknown Device")
                .ipAddress(ipAddress != null ? ipAddress : "0.0.0.0")
                .createdAt(Instant.now())
                .timeToLiveSeconds(refreshTokenTtlSeconds)
                .build();

        refreshTokenRepository.save(refreshToken);
        recordActiveSession(userId, refreshToken);

        log.info("Successfully issued new refresh token for userId: {}, deviceId: {}", userId, assignedDeviceId);
        return refreshToken;
    }

    /**
     * Track active device session in Redis HASH (user:sessions:{userId}).
     */
    public void recordActiveSession(String userId, RefreshToken refreshToken) {
        if (userId == null || refreshToken == null) {
            log.warn("Skipping active session record due to null parameters.");
            return;
        }

        String key = "user:sessions:" + userId;
        UserSession session = UserSession.builder()
                .deviceId(refreshToken.getDeviceId())
                .deviceName(refreshToken.getUserAgent())
                .ipAddress(refreshToken.getIpAddress())
                .lastActiveAt(Instant.now())
                .refreshTokenId(refreshToken.getId())
                .build();

        // Save session object inside Redis hash map
        redisTemplate.opsForHash().put(key, refreshToken.getDeviceId(), session);

        // 🔒 FIX: Explicitly set TTL on Hash key so abandoned session keys don't leak memory forever
        redisTemplate.expire(key, refreshTokenTtlSeconds, TimeUnit.SECONDS);
    }

    /**
     * Verify and rotate Refresh Token (Invalidates old token and issues a new one).
     */
    public Optional<RefreshToken> verifyAndRotate(String token, String userAgent, String ipAddress) {
        if (token == null || token.isBlank()) {
            log.warn("Attempted refresh token rotation with null or empty token string.");
            return Optional.empty();
        }

        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findById(token);

        if (tokenOpt.isEmpty()) {
            log.warn("Security Alert: Attempted token rotation with invalid or expired refresh token: {}", token);
            return Optional.empty();
        }

        RefreshToken oldToken = tokenOpt.get();

        // Invalidate old refresh token (Token Rotation Security Pattern)
        refreshTokenRepository.delete(oldToken);

        // Issue new rotated refresh token for the same user and device
        RefreshToken newToken = createRefreshToken(
                oldToken.getUserId(),
                oldToken.getEmail(),
                oldToken.getDeviceId(),
                userAgent,
                ipAddress
        );

        log.info("Rotated refresh token for userId: {}, deviceId: {}", oldToken.getUserId(), oldToken.getDeviceId());
        return Optional.of(newToken);
    }

    /**
     * Sign out of current device.
     */
    public void revokeRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        refreshTokenRepository.findById(token).ifPresentOrElse(rt -> {
            refreshTokenRepository.delete(rt);
            String key = "user:sessions:" + rt.getUserId();
            redisTemplate.opsForHash().delete(key, rt.getDeviceId());
            log.info("Revoked refresh token and device session for userId: {}, deviceId: {}", rt.getUserId(), rt.getDeviceId());
        }, () -> log.debug("Token revocation skipped: Token not found in Redis store."));
    }

    /**
     * Sign out of ALL devices for a user.
     */
    public void revokeAllSessions(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID must not be null or empty when revoking sessions.");
        }

        refreshTokenRepository.deleteByUserId(userId);
        String key = "user:sessions:" + userId;
        redisTemplate.delete(key);

        log.info("Revoked all active sessions and refresh tokens for userId: {}", userId);
    }

    /**
     * Get all active logged-in device sessions for a user.
     */
    public List<UserSession> getActiveSessions(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID must not be null or empty.");
        }

        String key = "user:sessions:" + userId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        if (entries.isEmpty()) {
            return Collections.emptyList();
        }

        return entries.values().stream()
                .filter(UserSession.class::isInstance)
                .map(UserSession.class::cast)
                .toList();
    }
}