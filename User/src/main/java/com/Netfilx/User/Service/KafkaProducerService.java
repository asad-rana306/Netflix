package com.Netfilx.User.Service;

import com.Netfilx.User.Config.KafkaTopicConfig;
import com.Netfilx.User.Event.UserRegisteredEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes a UserRegisteredEvent asynchronously to Kafka with full exception handling.
     *
     * @param event The registration event containing user details.
     */
    public void sendUserRegisteredEvent(UserRegisteredEvent event) {
        // 1. Defensive Input Validation
        if (event == null) {
            log.error("Failed to dispatch Kafka event: UserRegisteredEvent payload is null.");
            throw new IllegalArgumentException("UserRegisteredEvent must not be null.");
        }

        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            log.error("Failed to dispatch Kafka event: UserId is null or empty.");
            throw new IllegalArgumentException("UserRegisteredEvent userId must not be null or empty.");
        }

        String topic = KafkaTopicConfig.USER_EVENTS_TOPIC;
        String key = event.getUserId();

        // 2. PII Data Protection (Log userId instead of raw user email)
        log.info("Initiating publish for UserRegisteredEvent to topic '{}' for userId: {}", topic, key);

        try {
            // 3. Asynchronous Execution with CompletableFuture Callback
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Successfully dispatched UserRegisteredEvent to topic '{}' [Partition: {}, Offset: {}] for userId: {}",
                            topic,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(),
                            key);
                } else {
                    log.error("Async failure delivering UserRegisteredEvent to topic '{}' for userId: {}. Reason: {}",
                            topic, key, ex.getMessage(), ex);
                }
            });

        } catch (Exception e) {
            // 4. Catch Synchronous Errors (Serialization failures, missing metadata, template errors)
            log.error("Synchronous failure while submitting event to Kafka template for userId: {}", key, e);
            throw new RuntimeException("Unable to publish user registration event to Kafka.", e);
        }
    }
}