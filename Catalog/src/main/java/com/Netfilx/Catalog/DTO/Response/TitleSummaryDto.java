package com.Netfilx.Catalog.DTO.Response;

import java.util.Set;
import java.util.UUID;

public record TitleSummaryDto(
        UUID id,
        String title,
        String type,
        String maturityRating,
        String thumbnailUrl,
        Integer releaseYear,
        Set<String> genres
) {}