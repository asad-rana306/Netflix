package com.Netfilx.User.Service;

import com.Netfilx.User.Config.KafkaTopicConfig;
import com.Netfilx.User.Event.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendUserRegisteredEvent(UserRegisteredEvent event) {
        log.info("Publishing UserRegisteredEvent to Kafka topic '{}' for user: {}",
                KafkaTopicConfig.USER_EVENTS_TOPIC, event.getEmail());

        kafkaTemplate.send(KafkaTopicConfig.USER_EVENTS_TOPIC, event.getUserId(), event);
    }
}