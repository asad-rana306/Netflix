package com.Netfilx.User.Controller;

import com.Netfilx.User.DTO.Request.CreateProfileRequest;
import com.Netfilx.User.DTO.Request.VerifyPinRequest;
import com.Netfilx.User.DTO.Response.ProfileResponse;
import com.Netfilx.User.Entity.Profile;
import com.Netfilx.User.Entity.User;
import com.Netfilx.User.Repository.ProfileRepository;
import com.Netfilx.User.Service.ProfileService;
import com.Netfilx.User.Service.S3Service;
import com.Netfilx.User.Service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final UserService userService;
    private final ProfileRepository profileRepository;
    private final S3Service s3Service;

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

    @PostMapping("/users/{userId}/profiles")
    public ResponseEntity<?> createProfile(
            @PathVariable UUID userId,
            @RequestParam("profileName") String profileName,
            @RequestParam("avatarUrl") String avatarUrl) {

        User user = userService.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getProfiles().size() >= 5) {
            return ResponseEntity.badRequest().body("Maximum limit of 5 profiles reached.");
        }

        Profile profile = new Profile();
        profile.setUser(user);
        profile.setProfileName(profileName);
        profile.setAvatarUrl(avatarUrl);

        profileRepository.save(profile);
        return ResponseEntity.ok(profile);
    }

    /**
     * Uploads avatar image to S3 folder "avatars"
     */
    @PostMapping("/avatar")
    public ResponseEntity<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        try {
            // FIXED: Swap arguments to match S3Service signature: uploadFile(String folder, MultipartFile file)
            String fileUrl = s3Service.uploadFile("avatars", file);
            return ResponseEntity.ok(fileUrl);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Failed to upload image: " + e.getMessage());
        }
    }

    // ==================== HELPER METHODS ====================

    private String getAuthenticatedEmail(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            log.error("Security Violation: Unauthenticated or null principal in SecurityContext.");
            throw new SecurityException("User authentication principal is missing or invalid.");
        }
        return authentication.getName().trim().toLowerCase();
    }
}