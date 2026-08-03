package com.Netflix.Notification.listener;

import com.Netflix.Notification.DTO.PasswordResetRequestedEvent;
import com.Netflix.Notification.DTO.UserRegisteredEvent;
import com.Netflix.Notification.Service.EmailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventListener {

    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "user-events",
            groupId = "${spring.kafka.consumer.group-id:notification-group}"
    )
    public void handleUserEvent(
            @Payload String rawJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received user event from topic: {}, offset: {}", topic, offset);

        try {
            JsonNode root = objectMapper.readTree(rawJson);

            // Password reset check
            if (root.has("resetToken") || root.has("resetUrl")) {
                PasswordResetRequestedEvent event = objectMapper.treeToValue(root, PasswordResetRequestedEvent.class);
                log.info("Processing PasswordResetRequestedEvent for user: {}", event.getEmail());
                emailService.sendPasswordResetEmail(event);

                // Flexible user registration check (supports planType, verificationToken, or plain email)
            } else if (root.has("planType") || root.has("registeredAt") || root.has("verificationToken") || root.has("email")) {
                UserRegisteredEvent event = objectMapper.treeToValue(root, UserRegisteredEvent.class);
                log.info("Processing UserRegisteredEvent for user: {}", event.getEmail());
                emailService.sendWelcomeEmail(event);

            } else {
                log.warn("Unrecognized payload structure in topic user-events at offset {}: {}", offset, rawJson);
            }
        } catch (Exception e) {
            log.error("Failed to process event from user-events topic at offset: {}", offset, e);
            throw new RuntimeException("Error processing user-events payload", e);
        }
    }
}