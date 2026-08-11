package com.Netflix.payment_service.client;

import com.Netflix.payment_service.DTO.UpdateSubscriptionRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    @PutMapping("/internal/users/{userId}/subscription")
    void updateSubscription(
            @PathVariable("userId") String userId,
            @RequestBody UpdateSubscriptionRequest request
    );
}