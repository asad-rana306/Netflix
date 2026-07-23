package com.Netfilx.User.DTO.Response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponse {

    private UUID id;
    private String profileName;
    private String avatarUrl;
    private Boolean isKids;
    private String maturityRating;
    private Boolean hasPin; // Indicates if PIN is required to open this profile
    private LocalDateTime createdAt;
}