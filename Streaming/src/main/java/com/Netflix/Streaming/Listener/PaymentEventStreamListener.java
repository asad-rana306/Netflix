package com.Netflix.Streaming.Listener;

import com.Netflix.Streaming.DTO.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventStreamListener {

    private final StringRedisTemplate redisTemplate;

    @KafkaListener(topics = "payment-events", groupId = "streaming-service-group")
    public void handlePaymentEvent(PaymentEvent event) {
        if ("PAYMENT_CANCELED".equalsIgnoreCase(event.eventType())
                || "PAYMENT_FAILED".equalsIgnoreCase(event.eventType())) {

            String redisKey = "user:" + event.userId() + ":subscription";
            redisTemplate.delete(redisKey);
            log.info(">>> Evicted Redis subscription key [{}] due to event: {}", redisKey, event.eventType());
        }
    }
}