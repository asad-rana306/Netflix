package com.Netfilx.User.Entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession implements Serializable {

    private String deviceId;
    private String deviceName;
    private String ipAddress;
    private Instant lastActiveAt;
    private String refreshTokenId;
}