package com.Netflix.Gateway.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                // 1. DISABLE CSRF for stateless REST APIs & JWT auth
                .csrf(csrf -> csrf.disable())

                // 2. RELAX CSP Headers to allow Vite/React dev server 'eval' & inline scripts
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("script-src 'self' 'unsafe-inline' 'unsafe-eval' http://* https://*")
                        )
                )

                // 3. Configure Authorization Rules
                .authorizeExchange(exchanges -> exchanges
                        // Allow ALL OPTIONS preflight requests across Wi-Fi
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Allow public endpoints without authentication
                        .pathMatchers("/public/**", "/api/v1/auth/**").permitAll()
                        // Any other endpoint allowed
                        .anyExchange().permitAll()
                );

        return http.build();
    }
}