package com.Netfilx.User.Controller;

import com.Netfilx.User.DTO.Request.CreateProfileRequest;
import com.Netfilx.User.DTO.Request.VerifyPinRequest;
import com.Netfilx.User.DTO.Response.ProfileResponse;
import com.Netfilx.User.Service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    /**
     * Creates a new profile for the authenticated user account.
     */
    @PostMapping
    public ResponseEntity<ProfileResponse> createProfile(
            Authentication authentication,
            @Valid @RequestBody CreateProfileRequest request) {

        String email = getAuthenticatedEmail(authentication);
        log.info("REST request to create profile for user: {}", email);

        ProfileResponse response = profileService.createProfile(email, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves all profiles associated with the authenticated user account.
     */
    @GetMapping
    public ResponseEntity<List<ProfileResponse>> getProfiles(Authentication authentication) {
        String email = getAuthenticatedEmail(authentication);
        log.debug("REST request to fetch all profiles for user: {}", email);

        List<ProfileResponse> profiles = profileService.getUserProfiles(email);
        return ResponseEntity.ok(profiles);
    }

    /**
     * Verifies the PIN for a specific profile while enforcing ownership validation.
     */
    @PostMapping("/{profileId}/verify-pin")
    public ResponseEntity<Map<String, Object>> verifyPin(
            Authentication authentication,
            @PathVariable UUID profileId,
            @Valid @RequestBody VerifyPinRequest request) {

        String email = getAuthenticatedEmail(authentication);
        log.info("REST request to verify PIN for profile ID: {} by user: {}", profileId, email);

        // 🔒 Enforces user ownership check in service layer
        boolean isValid = profileService.verifyPin(email, profileId, request);

        if (isValid) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "PIN verified successfully"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Incorrect PIN"
            ));
        }
    }

    /**
     * Deletes a profile owned by the authenticated user account.
     */
    @DeleteMapping("/{profileId}")
    public ResponseEntity<Void> deleteProfile(
            Authentication authentication,
            @PathVariable UUID profileId) {

        String email = getAuthenticatedEmail(authentication);
        log.info("REST request to delete profile ID: {} for user: {}", profileId, email);

        profileService.deleteProfile(email, profileId);
        return ResponseEntity.noContent().build();
    }

    // ==================== HELPER METHODS ====================

    /**
     * Safely extracts and normalizes the user email from the SecurityContext Authentication object.
     */
    private String getAuthenticatedEmail(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            log.error("Security Violation: Unauthenticated or null principal in SecurityContext.");
            throw new SecurityException("User authentication principal is missing or invalid.");
        }
        return authentication.getName().trim().toLowerCase();
    }
}