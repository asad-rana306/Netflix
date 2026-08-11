package com.Netflix.payment_service.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateSubscriptionRequest {
    private String planTier;
    private String status;
    private String stripeCustomerId;
}
