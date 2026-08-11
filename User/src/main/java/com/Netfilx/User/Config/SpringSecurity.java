package com.Netfilx.User.Config;

import com.Netfilx.User.filter.JwtFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SpringSecurity {

    private final JwtFilter jwtFilter;
    private final ObjectMapper objectMapper;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:8000}")
    private List<String> allowedOrigins;

    /**
     * Password encoder utilizing BCrypt with a logarithmic strength factor of 12.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Enable CORS with configurable origins
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. Disable CSRF for stateless REST APIs & enable Frame Options (Clickjacking protection)
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny()) // Protects against Clickjacking attacks
                )

                // 3. Configure Custom Exception Handlers for 401 & 403 Security Exceptions
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(customAccessDeniedHandler())
                )

                // 4. Configure Request Route Authorizations
                // 4. Configure Request Route Authorizations
                .authorizeHttpRequests(auth -> auth
                        // Public Swagger & Open API Documentation
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // Health Check Probes & Internal Error Forwarding
                        .requestMatchers("/actuator/health", "/actuator/info", "/error").permitAll()

                        // Public Authentication & Registration Endpoints
                        .requestMatchers("/public/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/internal/**").permitAll()

                        // Admin Restricted Endpoints
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // Global Catch-All
                        .anyRequest().authenticated()
                )

                // 5. Enforce Stateless Session Management
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );

        // Inject custom JWT verification filter before UsernamePasswordAuthenticationFilter
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Defines CORS settings permitting requests from frontend applications or API Gateway.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Device-Id", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    // ==================== CUSTOM EXCEPTION HANDLERS ====================

    /**
     * Intercepts unauthenticated requests and returns a structured JSON 401 Unauthorized response.
     */
    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            log.warn("Unauthorized security access attempt to URI '{}': {}", request.getRequestURI(), authException.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            Map<String, Object> body = Map.of(
                    "success", false,
                    "status", 401,
                    "error", "Unauthorized",
                    "message", "Full authentication is required to access this resource.",
                    "path", request.getRequestURI()
            );

            objectMapper.writeValue(response.getOutputStream(), body);
        };
    }

    /**
     * Intercepts unauthorized requests and returns a structured JSON 403 Forbidden response.
     */
    @Bean
    public AccessDeniedHandler customAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            log.warn("Access denied for URI '{}': {}", request.getRequestURI(), accessDeniedException.getMessage());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            Map<String, Object> body = Map.of(
                    "success", false,
                    "status", 403,
                    "error", "Forbidden",
                    "message", "You do not have permission to access this resource.",
                    "path", request.getRequestURI()
            );

            objectMapper.writeValue(response.getOutputStream(), body);
        };
    }
}