package com.Netfilx.User.Config;

import com.Netfilx.User.filter.JwtFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SpringSecurity {

    private final JwtFilter jwtFilter;

    public SpringSecurity(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Enable CORS with custom configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. Disable CSRF for stateless REST APIs
                .csrf(csrf -> csrf.disable())

                // 3. Configure Authorizations
                .authorizeHttpRequests(auth -> auth
                        // Public Swagger & API Documentation
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/journal/swagger-ui/**",
                                "/journal/v3/api-docs/**",
                                "/journal/swagger-ui.html"
                        ).permitAll()

                        // Public Auth Endpoints (Signup, Login, Health)
                        .requestMatchers("/public/**").permitAll()
                        .requestMatchers("/journal/public").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/concerts").permitAll()

                        // Admin Endpoints
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/concerts").hasRole("ADMIN")

                        // Global Catch-All (Profiles & protected routes require JWT)
                        .anyRequest().authenticated()
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );

        // Inject JWT verification filter
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // 4. Define CORS Bean to allow requests from React Vite (Port 5173)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allow both direct frontend and API Gateway origins
        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:8000"
        ));

        // Allowed HTTP Methods
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));

        // Allowed Headers
        configuration.setAllowedHeaders(List.of("*"));

        // Allow Credentials / Bearer Tokens
        configuration.setAllowCredentials(true);

        // Cache preflight response for 1 hour
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}