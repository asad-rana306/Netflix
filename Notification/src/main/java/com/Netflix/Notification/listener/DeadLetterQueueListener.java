package com.Netflix.Notification.listener;


import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DeadLetterQueueListener {

    @KafkaListener(
            topics = "notification-events-dlq",
            groupId = "notification-dlq-group"
    )
    public void handleDeadLetterRecord(
            @Payload String failedPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.error("ALERT [DLQ RECORD]: Unprocessable event captured in topic '{}' at offset {}. Payload: {}",
                topic, offset, failedPayload);

        // Potential extension points:
        // 1. Persist record to PostgreSQL 'failed_notifications' table for manual admin replay.
        // 2. Trigger PagerDuty / Slack alert hook for ops notification.
    }
}
