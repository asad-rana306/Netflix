package com.Netfilx.Catalog.DTO.Response;


import java.util.Set;
import java.util.UUID;

public record TitleResponse(
        UUID id,
        String title,
        String description,
        String type,
        String maturityRating,
        String thumbnailUrl,
        String hlsMasterUrl,
        Integer releaseYear,
        Set<String> genres
) {}
