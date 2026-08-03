package com.Netflix.Notification.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PasswordResetRequestedEvent {
    private String eventId;
    private String userId;
    private String email;
    private String firstName;
    private String resetToken;
    private String resetUrl;
    private Instant expiresAt;
}