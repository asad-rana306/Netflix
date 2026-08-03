//package com.Netflix.Streaming.Service;
//
//
//
//import com.Netflix.Streaming.DTO.Response.SubscriptionStatusResponse;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.stereotype.Service;
//
//import java.util.concurrent.TimeUnit;
//
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class StreamAccessService {
//
//    private final StringRedisTemplate redisTemplate;
//    private final PaymentServiceClient paymentServiceClient;
//
//    private static final String REDIS_SUB_KEY_PREFIX = "user:";
//
//    public boolean canUserStream(String userId) {
//        if (userId == null || userId.isBlank()) {
//            return false;
//        }
//
//        String redisKey = REDIS_SUB_KEY_PREFIX + userId + ":subscription";
//
//        // 1. FAST PATH: Check Redis
//        try {
//            String cachedStatus = redisTemplate.opsForValue().get(redisKey);
//            if (cachedStatus != null) {
//                log.info(">>> [Cache Hit] Redis key [{}] = {}", redisKey, cachedStatus);
//                return cachedStatus.startsWith("ACTIVE");
//            }
//        } catch (Exception e) {
//            log.warn(">>> Redis connection failed, executing fallback DB check: {}", e.getMessage());
//        }
//
//        // 2. SLOW PATH (Cache Miss): Fall back to PAYMENT-SERVICE via Feign
//        log.info(">>> [Cache Miss] Key [{}] not in Redis. Fetching from PAYMENT-SERVICE...", redisKey);
//
//        try {
//            SubscriptionStatusResponse response = paymentServiceClient.getSubscriptionStatus(userId);
//
//            if (response != null && response.active()) {
//                String cacheValue = "ACTIVE_" + (response.planTier() != null ? response.planTier() : "PREMIUM");
//
//                // Cache active status for 30 Days
//                cacheValueInRedis(redisKey, cacheValue, 30, TimeUnit.DAYS);
//                return true;
//            } else {
//                // Negative Caching: Cache INACTIVE for 5 minutes to prevent DB hammering
//                cacheValueInRedis(redisKey, "INACTIVE", 5, TimeUnit.MINUTES);
//                return false;
//            }
//        } catch (Exception e) {
//            log.error(">>> Failed to fetch subscription status for userId: {}", userId, e);
//            return false; // Fail secure: block stream if status cannot be verified
//        }
//    }
//
//    private void cacheValueInRedis(String key, String value, long timeout, TimeUnit unit) {
//        try {
//            redisTemplate.opsForValue().set(key, value, timeout, unit);
//        } catch (Exception e) {
//            log.warn(">>> Failed to write to Redis: {}", e.getMessage());
//        }
//    }
//}

package com.Netflix.Streaming.Service;

import com.Netflix.Streaming.DTO.Response.SubscriptionStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamAccessService {

    // 🔴 REDIS TEMPORARILY DISABLED FOR RAW BASELINE LOAD TESTING
    // private final StringRedisTemplate redisTemplate;
    // private final PaymentServiceClient paymentServiceClient;

    public boolean canUserStream(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }

        // Bypass Redis and Payment-Service check completely for baseline testing
        return true;
    }
}