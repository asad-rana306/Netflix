package com.Netfilx.User.Service;

import com.Netfilx.User.DTO.Request.CreateProfileRequest;
import com.Netfilx.User.DTO.Request.VerifyPinRequest;
import com.Netfilx.User.DTO.Response.ProfileResponse;
import com.Netfilx.User.Entity.Profile;
import com.Netfilx.User.Entity.User;
import com.Netfilx.User.Repository.ProfileRepository;
import com.Netfilx.User.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final int MAX_PROFILES_PER_USER = 5;
    private static final String DEFAULT_AVATAR = "default_avatar.png";
    private static final String DEFAULT_MATURITY_RATING = "TV-MA";

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Creates a new profile for the authenticated user.
     */
    @Transactional
    public ProfileResponse createProfile(String email, CreateProfileRequest request) {
        log.info("Attempting to create profile for user: {}", email);

        User user = getUserByEmail(email);

        // Input Validation
        if (request == null || request.getProfileName() == null || request.getProfileName().trim().isEmpty()) {
            throw new IllegalArgumentException("Profile name cannot be null or empty.");
        }

        // Max profile limit enforcement
        long currentProfileCount = profileRepository.countByUserId(user.getId());
        if (currentProfileCount >= MAX_PROFILES_PER_USER) {
            log.warn("Profile creation failed for user {}: Maximum limit of {} reached.", email, MAX_PROFILES_PER_USER);
            throw new IllegalStateException("Maximum limit of " + MAX_PROFILES_PER_USER + " profiles reached for this account.");
        }

        // Validate and encode PIN if provided
        String encodedPin = null;
        if (request.getPin() != null && !request.getPin().isBlank()) {
            validatePinFormat(request.getPin());
            encodedPin = passwordEncoder.encode(request.getPin());
        }

        Profile profile = Profile.builder()
                .user(user)
                .profileName(request.getProfileName().trim())
                .avatarUrl(request.getAvatarUrl() != null && !request.getAvatarUrl().isBlank()
                        ? request.getAvatarUrl() : DEFAULT_AVATAR)
                .isKids(Boolean.TRUE.equals(request.getIsKids()))
                .maturityRating(request.getMaturityRating() != null && !request.getMaturityRating().isBlank()
                        ? request.getMaturityRating() : DEFAULT_MATURITY_RATING)
                .pin(encodedPin)
                .build();

        Profile savedProfile = profileRepository.save(profile);
        log.info("Successfully created profile ID: {} for user: {}", savedProfile.getId(), email);

        return mapToDto(savedProfile);
    }

    /**
     * Retrieves all profiles belonging to the authenticated user.
     */
    @Transactional(readOnly = true)
    public List<ProfileResponse> getUserProfiles(String email) {
        User user = getUserByEmail(email);

        return profileRepository.findByUserId(user.getId())
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    /**
     * Verifies the profile PIN while enforcing user ownership security checks.
     */
    @Transactional(readOnly = true)
    public boolean verifyPin(String email, UUID profileId, VerifyPinRequest request) {
        if (request == null || request.getPin() == null || request.getPin().isBlank()) {
            throw new IllegalArgumentException("PIN verification request cannot be null or empty.");
        }

        User user = getUserByEmail(email);
        Profile profile = getProfileById(profileId);

        // Security Check: Enforce profile ownership (Prevent IDOR vulnerability)
        validateProfileOwnership(user, profile);

        // If profile has no PIN configured
        if (profile.getPin() == null || profile.getPin().isBlank()) {
            return true;
        }

        boolean isMatch = passwordEncoder.matches(request.getPin(), profile.getPin());
        if (!isMatch) {
            log.warn("Invalid PIN attempt for profile ID: {} by user: {}", profileId, email);
        }

        return isMatch;
    }

    /**
     * Deletes a user profile with ownership verification and minimum profile safeguard.
     */
    @Transactional
    public void deleteProfile(String email, UUID profileId) {
        log.info("Attempting to delete profile ID: {} for user: {}", profileId, email);

        User user = getUserByEmail(email);
        Profile profile = getProfileById(profileId);

        // Security Check: Enforce profile ownership
        validateProfileOwnership(user, profile);

        // Safeguard: Prevent deleting the account's last remaining profile
        long count = profileRepository.countByUserId(user.getId());
        if (count <= 1) {
            log.warn("Failed to delete profile ID {}: Account must have at least one profile.", profileId);
            throw new IllegalStateException("Cannot delete the last remaining profile on the account.");
        }

        profileRepository.delete(profile);
        log.info("Successfully deleted profile ID: {} for user: {}", profileId, email);
    }

    // ==================== HELPER METHODS ====================

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found for email: " + email));
    }

    private Profile getProfileById(UUID profileId) {
        return profileRepository.findById(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found with ID: " + profileId));
    }

    private void validateProfileOwnership(User user, Profile profile) {
        if (!Objects.equals(profile.getUser().getId(), user.getId())) {
            log.error("Security Violation: User ID {} attempted unauthorized access to profile ID {} owned by User ID {}",
                    user.getId(), profile.getId(), profile.getUser().getId());
            throw new SecurityException("Unauthorized access: You do not own this profile.");
        }
    }

    private void validatePinFormat(String pin) {
        // Enforces a strict 4-digit numeric PIN format
        if (!pin.matches("^\\d{4}$")) {
            throw new IllegalArgumentException("Profile PIN must be exactly 4 numeric digits.");
        }
    }

    private ProfileResponse mapToDto(Profile profile) {
        return ProfileResponse.builder()
                .id(profile.getId())
                .profileName(profile.getProfileName())
                .avatarUrl(profile.getAvatarUrl())
                .isKids(Boolean.TRUE.equals(profile.getIsKids()))
                .maturityRating(profile.getMaturityRating())
                .hasPin(profile.getPin() != null && !profile.getPin().isBlank())
                .createdAt(profile.getCreatedAt())
                .build();
    }
}