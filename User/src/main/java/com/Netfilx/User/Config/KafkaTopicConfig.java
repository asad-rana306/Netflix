package com.Netfilx.User.Config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaTopicConfig {

    public static final String USER_EVENTS_TOPIC = "user-events";

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * Dynamically configures KafkaAdmin using injected environment properties,
     * allowing multi-environment deployments (Docker, Kubernetes, AWS) without hardcoded hosts.
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        KafkaAdmin admin = new KafkaAdmin(configs);
        // Prevents Kafka broker connection failures from halting Spring Boot container startup
        admin.setFatalIfBrokerNotAvailable(false);
        return admin;
    }

    /**
     * Provisions the 'user-events' topic on startup if it does not already exist on the broker.
     */
    @Bean
    public NewTopic userEventsTopic() {
        return TopicBuilder.name(USER_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}