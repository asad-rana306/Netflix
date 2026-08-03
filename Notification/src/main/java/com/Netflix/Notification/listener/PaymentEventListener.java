package com.Netflix.Notification.listener;

import com.Netflix.Notification.DTO.PaymentFailedEvent;
import com.Netflix.Notification.DTO.PaymentSucceededEvent;
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
public class PaymentEventListener {

    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "payment-events",
            groupId = "${spring.kafka.consumer.group-id:notification-group}"
    )
    public void handlePaymentEvent(
            @Payload String rawJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received payment event from topic: {}, offset: {}", topic, offset);

        try {
            JsonNode root = objectMapper.readTree(rawJson);

            if (root.has("failureReason") || root.has("retryPaymentUrl")) {
                PaymentFailedEvent event = objectMapper.treeToValue(root, PaymentFailedEvent.class);
                log.info("Processing PaymentFailedEvent for user: {}", event.getUserEmail());
                emailService.sendPaymentFailureEmail(event);
            } else if (root.has("invoicePdfUrl") || root.has("transactionId")) {
                PaymentSucceededEvent event = objectMapper.treeToValue(root, PaymentSucceededEvent.class);
                log.info("Processing PaymentSucceededEvent for transaction: {}", event.getTransactionId());
                emailService.sendPaymentSuccessEmail(event);
            } else {
                log.warn("Unrecognized payload structure in topic payment-events at offset {}: {}", offset, rawJson);
            }
        } catch (Exception e) {
            log.error("Failed to process event from payment-events topic at offset: {}", offset, e);
            throw new RuntimeException("Error processing payment-events payload", e);
        }
    }
}