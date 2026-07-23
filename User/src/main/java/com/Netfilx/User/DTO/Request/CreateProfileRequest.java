package com.Netfilx.User.DTO.Request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateProfileRequest {

    @NotBlank(message = "Profile name is required")
    @Size(max = 50, message = "Profile name must be under 50 characters")
    private String profileName;

    private String avatarUrl;
    private Boolean isKids;
    private String maturityRating;

    @Pattern(regexp = "^\\d{4}$", message = "PIN must be exactly 4 digits")
    private String pin; // Optional
}