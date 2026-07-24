package com.Netfilx.User.Service;

import com.Netfilx.User.Entity.RefreshToken;
import com.Netfilx.User.Entity.UserSession;
import com.Netfilx.User.Repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

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
     * Create and store a new Refresh Token in Redis with 30-day TTL.
     */
    public RefreshToken createRefreshToken(String userId, String email, String deviceId, String userAgent, String ipAddress) {
        String token = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .id(token)
                .userId(userId)
                .email(email)
                .deviceId(deviceId != null ? deviceId : UUID.randomUUID().toString())
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .createdAt(Instant.now())
                .timeToLiveSeconds(refreshTokenTtlSeconds)
                .build();

        refreshTokenRepository.save(refreshToken);
        recordActiveSession(userId, refreshToken);
        return refreshToken;
    }

    /**
     * Track active device session in Redis HASH (user:sessions:{userId}).
     */
    public void recordActiveSession(String userId, RefreshToken refreshToken) {
        String key = "user:sessions:" + userId;
        UserSession session = UserSession.builder()
                .deviceId(refreshToken.getDeviceId())
                .deviceName(refreshToken.getUserAgent())
                .ipAddress(refreshToken.getIpAddress())
                .lastActiveAt(Instant.now())
                .refreshTokenId(refreshToken.getId())
                .build();

        redisTemplate.opsForHash().put(key, refreshToken.getDeviceId(), session);
    }

    /**
     * Verify and rotate Refresh Token (Invalidates old token and returns a new one).
     */
    public Optional<RefreshToken> verifyAndRotate(String token, String userAgent, String ipAddress) {
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findById(token);

        if (tokenOpt.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken oldToken = tokenOpt.get();
        // Invalidate used refresh token
        refreshTokenRepository.delete(oldToken);

        // Issue new rotated refresh token for the same device
        RefreshToken newToken = createRefreshToken(
                oldToken.getUserId(),
                oldToken.getEmail(),
                oldToken.getDeviceId(),
                userAgent,
                ipAddress
        );

        return Optional.of(newToken);
    }

    /**
     * Sign out of current device.
     */
    public void revokeRefreshToken(String token) {
        refreshTokenRepository.findById(token).ifPresent(rt -> {
            refreshTokenRepository.delete(rt);
            String key = "user:sessions:" + rt.getUserId();
            redisTemplate.opsForHash().delete(key, rt.getDeviceId());
        });
    }

    /**
     * Sign out of ALL devices for a user.
     */
    public void revokeAllSessions(String userId) {
        refreshTokenRepository.deleteByUserId(userId);
        String key = "user:sessions:" + userId;
        redisTemplate.delete(key);
    }

    /**
     * Get all active logged-in device sessions for a user.
     */
    public List<Object> getActiveSessions(String userId) {
        String key = "user:sessions:" + userId;
        return redisTemplate.opsForHash().values(key);
    }
}
