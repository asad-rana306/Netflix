package com.Netfilx.User.Controller;

import com.Netfilx.User.DTO.Request.CreateProfileRequest;
import com.Netfilx.User.DTO.Request.VerifyPinRequest;
import com.Netfilx.User.DTO.Response.ProfileResponse;
import com.Netfilx.User.Service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @PostMapping
    public ResponseEntity<ProfileResponse> createProfile(
            Authentication authentication,
            @Valid @RequestBody CreateProfileRequest request) {
        String email = authentication.getName(); // Extracted from JWT
        return ResponseEntity.status(HttpStatus.CREATED).body(profileService.createProfile(email, request));
    }

    @GetMapping
    public ResponseEntity<List<ProfileResponse>> getProfiles(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(profileService.getUserProfiles(email));
    }

    @PostMapping("/{profileId}/verify-pin")
    public ResponseEntity<?> verifyPin(
            @PathVariable UUID profileId,
            @Valid @RequestBody VerifyPinRequest request) {
        boolean isValid = profileService.verifyPin(profileId, request);
        if (isValid) {
            return ResponseEntity.ok("PIN verified successfully");
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Incorrect PIN");
        }
    }

    @DeleteMapping("/{profileId}")
    public ResponseEntity<Void> deleteProfile(
            Authentication authentication,
            @PathVariable UUID profileId) {
        String email = authentication.getName();
        profileService.deleteProfile(email, profileId);
        return ResponseEntity.noContent().build();
    }
}
