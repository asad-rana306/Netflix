package com.Netfilx.User.Controller;

import com.Netfilx.User.DTO.Request.LoginRequest;
import com.Netfilx.User.DTO.Request.RefreshTokenRequest;
import com.Netfilx.User.DTO.Request.SignupRequest;
import com.Netfilx.User.DTO.Request.UpdateSubscriptionRequest;
import com.Netfilx.User.DTO.Response.AuthResponse;
import com.Netfilx.User.Entity.Profile;
import com.Netfilx.User.Entity.RefreshToken;
import com.Netfilx.User.Entity.User;
import com.Netfilx.User.Entity.UserSession;
import com.Netfilx.User.Event.UserRegisteredEvent;
<<<<<<< HEAD
=======
import com.Netfilx.User.Repository.ProfileRepository;
>>>>>>> 08f2502 (attached S3 and successfully streamed on local)
import com.Netfilx.User.Service.*;
import com.Netfilx.User.Utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserDetailServiceImpl userDetailService;
    private final UserService userService;
    private final RedisSessionService redisSessionService;
    private final KafkaProducerService kafkaProducerService;
    private final S3Service s3Service;
<<<<<<< HEAD
=======
    private final ProfileRepository profileRepository;
>>>>>>> 08f2502 (attached S3 and successfully streamed on local)


    /**
     * Health check endpoint for API Gateway and discovery probes.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "message", "User Service Public API is operational"
        ));
    }

    @PostMapping("/{userId}/avatar")
    public ResponseEntity<Map<String, String>> uploadAvatar(
            @PathVariable UUID userId,
            @RequestParam("file") MultipartFile file) throws IOException {

        // 1. Store binary in S3 under 'profile-pictures/'
        String fileName = userId + "-" + file.getOriginalFilename();
        String s3Key = s3Service.uploadFile("profile-pictures", fileName, file);

        // 2. Persist s3Key into users table 'avatar_url' column
        userService.updateProfilePictureKey(userId, s3Key);

        return ResponseEntity.ok(Map.of(
                "userId", userId.toString(),
                "message", "Avatar uploaded successfully",
                "avatarUrl", s3Key
        ));
    }

    /**
     * Signup: Registers a new user account and emits a UserRegisteredEvent to Kafka.
     */
    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> signup(@Valid @RequestBody SignupRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        log.info("REST request to register new user account for email: {}", normalizedEmail);

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(request.getPassword()); // Encoded inside userService.saveNewUser

        User savedUser = userService.saveNewUser(user);
        String userIdStr = savedUser.getId() != null ? savedUser.getId().toString() : UUID.randomUUID().toString();

        // Emit asynchronous Kafka event for downstream notification service
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(userIdStr)
                .email(savedUser.getEmail())
                .verificationToken(UUID.randomUUID().toString())
                .createdAt(Instant.now())
                .build();

        kafkaProducerService.sendUserRegisteredEvent(event);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "message", "User registered successfully",
                "userId", userIdStr
        ));
    }

    /**
     * Login: Authenticates credentials, generates JWT access token, and stores session in Redis.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        log.info("REST request to authenticate user: {}", normalizedEmail);

        try {
            // 1. Authenticate credentials against Security Manager / Database
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
            );
        } catch (BadCredentialsException e) {
            log.warn("Authentication failed: Invalid credentials for email: {}", normalizedEmail);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Incorrect email or password"
            ));
        }

        // 2. Fetch persisted User entity to retrieve true database userId
        User user = userService.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user record not found in database."));

        UserDetails userDetails = userDetailService.loadUserByUsername(normalizedEmail);
        String accessToken = jwtUtil.generateToken(userDetails.getUsername());

        // 3. Extract request metadata
        String userAgent = httpRequest.getHeader("User-Agent");
        String ipAddress = getClientIp(httpRequest);
        String deviceId = httpRequest.getHeader("X-Device-Id");

        if (deviceId == null || deviceId.isBlank()) {
            deviceId = UUID.randomUUID().toString();
        }

        String userIdStr = user.getId() != null ? user.getId().toString() : user.getEmail();

        // 4. Create Refresh Token & store session in Redis
        RefreshToken refreshToken = redisSessionService.createRefreshToken(
                userIdStr,
                user.getEmail(),
                deviceId,
                userAgent,
                ipAddress
        );

        // 5. Build structured AuthResponse payload
        AuthResponse response = AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getId())
                .email(user.getEmail())
                .userId(userIdStr)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Refresh: Rotates an old Refresh Token for a new Access + Refresh Token pair.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        log.info("REST request to rotate refresh token");

        String userAgent = httpRequest.getHeader("User-Agent");
        String ipAddress = getClientIp(httpRequest);

        Optional<RefreshToken> newRefreshTokenOpt = redisSessionService.verifyAndRotate(
                request.getRefreshToken(), userAgent, ipAddress);

        if (newRefreshTokenOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Invalid or expired Refresh Token"
            ));
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
     * Logout: Revokes current device session from Redis.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("REST request to revoke active refresh token session");
        redisSessionService.revokeRefreshToken(request.getRefreshToken());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Successfully logged out"
        ));
    }

    /**
     * Logout All: Revokes all device sessions for the authenticated token holder.
     */
    @PostMapping("/logout-all")
    public ResponseEntity<Map<String, Object>> logoutAll(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("REST request to revoke all active sessions for token holder");

        // 🔒 SECURITY FIX: Verify incoming refresh token to safely resolve and revoke the owner's sessions
        Optional<RefreshToken> tokenOpt = redisSessionService.verifyAndRotate(request.getRefreshToken(), null, null);

        if (tokenOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Invalid or expired Refresh Token"
            ));
        }

        String userId = tokenOpt.get().getUserId();
        redisSessionService.revokeAllSessions(userId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Successfully logged out from all devices"
        ));
    }

    /**
     * View Active Device Sessions stored in Redis for the token holder.
     */
    @PostMapping("/sessions")
    public ResponseEntity<?> getActiveSessions(@Valid @RequestBody RefreshTokenRequest request) {
        log.debug("REST request to retrieve active sessions");

        // 🔒 SECURITY FIX: Verify token ownership before returning session details
        Optional<RefreshToken> tokenOpt = redisSessionService.verifyAndRotate(request.getRefreshToken(), null, null);

        if (tokenOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Invalid or expired Refresh Token"
            ));
        }

        String userId = tokenOpt.get().getUserId();
        List<UserSession> sessions = redisSessionService.getActiveSessions(userId);

        return ResponseEntity.ok(sessions);
    }

    // ==================== HELPER METHODS ====================

    /**
     * Extracts client IP address handling reverse proxy headers (X-Forwarded-For).
     */
    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }


    @PutMapping("/{userId}/subscription")
    public ResponseEntity<Void> updateSubscription(
            @PathVariable String userId,
            @Valid @RequestBody UpdateSubscriptionRequest request) {

        userService.updateSubscription(userId, request);
        return ResponseEntity.ok().build();
    }
}