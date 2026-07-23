package com.Netfilx.User.Service;

import com.Netfilx.User.DTO.Request.CreateProfileRequest;
import com.Netfilx.User.DTO.Request.VerifyPinRequest;
import com.Netfilx.User.DTO.Response.ProfileResponse;
import com.Netfilx.User.Entity.Profile;
import com.Netfilx.User.Entity.User;
import com.Netfilx.User.Repository.ProfileRepository;
import com.Netfilx.User.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public ProfileResponse createProfile(String email, CreateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (profileRepository.countByUserId(user.getId()) >= 5) {
            throw new IllegalStateException("Maximum limit of 5 profiles reached for this account.");
        }

        Profile profile = Profile.builder()
                .user(user)
                .profileName(request.getProfileName())
                .avatarUrl(request.getAvatarUrl() != null ? request.getAvatarUrl() : "default_avatar.png")
                .isKids(request.getIsKids() != null && request.getIsKids())
                .maturityRating(request.getMaturityRating() != null ? request.getMaturityRating() : "TV-MA")
                .pin(request.getPin() != null && !request.getPin().isBlank() ? passwordEncoder.encode(request.getPin()) : null)
                .build();

        Profile saved = profileRepository.save(profile);
        return mapToDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ProfileResponse> getUserProfiles(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return profileRepository.findByUserId(user.getId())
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    public boolean verifyPin(UUID profileId, VerifyPinRequest request) {
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        if (profile.getPin() == null) {
            return true; // No PIN set
        }

        return passwordEncoder.matches(request.getPin(), profile.getPin());
    }

    @Transactional
    public void deleteProfile(String email, UUID profileId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        if (!profile.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Unauthorized to delete this profile");
        }

        profileRepository.delete(profile);
    }

    private ProfileResponse mapToDto(Profile profile) {
        return ProfileResponse.builder()
                .id(profile.getId())
                .profileName(profile.getProfileName())
                .avatarUrl(profile.getAvatarUrl())
                .isKids(profile.getIsKids())
                .maturityRating(profile.getMaturityRating())
                .hasPin(profile.getPin() != null && !profile.getPin().isBlank())
                .createdAt(profile.getCreatedAt())
                .build();
    }
}
