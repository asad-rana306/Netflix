package com.Netfilx.User.Entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash("refresh_tokens")
public class RefreshToken implements Serializable {

    @Id
    private String id; // UUID or Token String

    @Indexed
    private String userId;

    private String email;

    private String deviceId;

    private String userAgent;

    private String ipAddress;

    private Instant createdAt;

    @TimeToLive
    private Long timeToLiveSeconds; // Redis handles auto-deletion on expiry
}