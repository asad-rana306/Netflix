package com.Netflix.Streaming.Service;

import com.Netflix.Streaming.DTO.Response.SubscriptionStatusResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "PAYMENT-SERVICE", path = "/api/v1/payments")
public interface PaymentServiceClient {

    @GetMapping("/status")
    SubscriptionStatusResponse getSubscriptionStatus(@RequestHeader("X-User-Id") String userId);
}