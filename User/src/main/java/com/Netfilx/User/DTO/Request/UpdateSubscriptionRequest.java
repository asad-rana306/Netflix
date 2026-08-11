package com.Netfilx.User.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UpdateSubscriptionRequest {
    @NotBlank
    private String planTier; // e.g., "STANDARD", "PREMIUM"

    @NotBlank
    private String status;   // "ACTIVE" or "INACTIVE"

    private String stripeCustomerId;
}