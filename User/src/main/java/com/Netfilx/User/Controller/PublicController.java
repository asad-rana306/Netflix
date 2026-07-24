package com.Netfilx.User.Controller;

import com.Netfilx.User.DTO.Request.LoginRequest;
import com.Netfilx.User.DTO.Request.RefreshTokenRequest;
import com.Netfilx.User.DTO.Request.SignupRequest;
import com.Netfilx.User.DTO.Response.AuthResponse;
import com.Netfilx.User.Entity.RefreshToken;
import com.Netfilx.User.Entity.User;
import com.Netfilx.User.Event.UserRegisteredEvent;
import com.Netfilx.User.Service.KafkaProducerService;
import com.Netfilx.User.Service.RedisSessionService;
import com.Netfilx.User.Service.UserDetailServiceImpl;
import com.Netfilx.User.Service.UserService;
import com.Netfilx.User.Utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/public")
public class PublicController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserDetailServiceImpl userDetailService;
    private final UserService userService;
    private final RedisSessionService redisSessionService;
    private final KafkaProducerService kafkaProducerService;

    @Autowired
    public PublicController(AuthenticationManager authenticationManager,
                            JwtUtil jwtUtil,
                            UserDetailServiceImpl userDetailService,
                            UserService userService,
                            RedisSessionService redisSessionService,
                            KafkaProducerService kafkaProducerService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userDetailService = userDetailService;
        this.userService = userService;
        this.redisSessionService = redisSessionService;
        this.kafkaProducerService = kafkaProducerService;
    }

    @GetMapping
    public String healthCheck() {
        return "Ok";
    }

    /**
     * Signup: Saves user and emits a UserRegisteredEvent to Kafka
     */
    @PostMapping("/signup")
    public ResponseEntity<String> signup(@Valid @RequestBody SignupRequest request) {
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(request.getPassword()); // userService.saveNewUser will encode this password

        User savedUser = userService.saveNewUser(user);

        // Emit asynchronous Kafka event for Notification Service
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(savedUser.getId() != null ? savedUser.getId().toString() : UUID.randomUUID().toString())
                .email(savedUser.getEmail())
                .verificationToken(UUID.randomUUID().toString())
                .createdAt(Instant.now())
                .build();

        kafkaProducerService.sendUserRegisteredEvent(event);

        return ResponseEntity.status(HttpStatus.CREATED).body("User Registered Successfully");
    }

    /**
     * Login: Authenticates user, generates Access Token, stores Refresh Token in Redis
     */
    private static final Logger log = LoggerFactory.getLogger(PublicController.class);

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            // 1. Authenticate credentials against PostgreSQL
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            // 2. Load UserDetails and generate short-lived JWT access token
            UserDetails userDetails = userDetailService.loadUserByUsername(request.getEmail());
            String accessToken = jwtUtil.generateToken(userDetails.getUsername());

            // 3. Extract request metadata
            String userAgent = httpRequest.getHeader("User-Agent");
            String ipAddress = httpRequest.getRemoteAddr();
            String deviceId = httpRequest.getHeader("X-Device-Id");

            // Fallback for null deviceId to prevent NPE
            if (deviceId == null || deviceId.isBlank()) {
                deviceId = UUID.randomUUID().toString();
            }

            // 4. Create Refresh Token & store session in Redis
            RefreshToken refreshToken = redisSessionService.createRefreshToken(
                    userDetails.getUsername(),
                    request.getEmail(),
                    deviceId,
                    userAgent,
                    ipAddress
            );

            // 5. Return AuthResponse containing both tokens
            AuthResponse response = AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken.getId())
                    .email(request.getEmail())
                    .userId(userDetails.getUsername())
                    .build();

            return ResponseEntity.ok(response);

        } catch (BadCredentialsException e) {
            log.warn("Authentication failed for email: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Incorrect email or password");
        } catch (Exception e) {
            // 💡 Print real error to console so we can see if Redis/JWT is failing!
            log.error("Internal error during login for user: {}", request.getEmail(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal server error during login: " + e.getMessage());
        }
    }
    /**
     * Refresh: Swaps an old Refresh Token for a new Access + Refresh Token pair
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        String userAgent = httpRequest.getHeader("User-Agent");
        String ipAddress = httpRequest.getRemoteAddr();

        Optional<RefreshToken> newRefreshTokenOpt = redisSessionService.verifyAndRotate(
                request.getRefreshToken(), userAgent, ipAddress);

        if (newRefreshTokenOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or expired Refresh Token");
        }

        RefreshToken newRefreshToken = newRefreshTokenOpt.get();
        UserDetails userDetails = userDetailService.loadUserByUsername(newRefreshToken.getEmail());
        String newAccessToken = jwtUtil.generateToken(userDetails.getUsername());

        AuthResponse response = AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken.getId())
                .email(newRefreshToken.getEmail())
                .userId(newRefreshToken.getUserId())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Logout: Revokes current device session from Redis
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@Valid @RequestBody RefreshTokenRequest request) {
        redisSessionService.revokeRefreshToken(request.getRefreshToken());
        return ResponseEntity.ok("Successfully logged out");
    }

    /**
     * Logout All: Revokes all device sessions for a specific user
     */
    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(@RequestParam String userId) {
        redisSessionService.revokeAllSessions(userId);
        return ResponseEntity.ok("Successfully logged out from all devices");
    }

    /**
     * View Active Device Sessions stored in Redis
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<Object>> getActiveSessions(@RequestParam String userId) {
        List<Object> sessions = redisSessionService.getActiveSessions(userId);
        return ResponseEntity.ok(sessions);
    }
}