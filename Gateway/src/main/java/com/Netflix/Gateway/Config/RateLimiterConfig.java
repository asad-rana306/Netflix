package com.Netflix.Gateway.Config;


import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Configuration
public class RateLimiterConfig {

    /**
     * IP-Based Key Resolver: Throttles requests per client IP address.
     * Checks X-Forwarded-For header first (if behind reverse proxies/load balancers),
     * falling back to the remote socket address.
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return Mono.just(xForwardedFor.split(",")[0].trim());
            }
            return Mono.just(
                    Objects.requireNonNull(exchange.getRequest().getRemoteAddress())
                            .getAddress()
                            .getHostAddress()
            );
        };
    }

    /**
     * User-Based Key Resolver: Throttles requests per logged-in user account
     * using the X-User-Id header injected by JwtAuthenticationFilter.
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just(userId);
            }
            // Fallback to IP address if request is unauthenticated
            return ipKeyResolver().resolve(exchange);
        };
    }
}