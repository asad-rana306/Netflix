package com.Netfilx.Catalog.DTO.Response;

import java.util.UUID;

public interface TitleProjection {
    UUID getId();
    String getTitle();
    String getDescription();
    String getType();
    String getMaturityRating();
    String getThumbnailUrl();
    String getHlsMasterUrl();
    Integer getReleaseYear();
    String getPreviewUrl();
}