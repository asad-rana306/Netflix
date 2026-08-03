package com.Netflix.Streaming.DTO.Request;


import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class ProgressUpdateRequest {
    @NotNull
    private UUID profileId;

    @NotNull
    private UUID titleId;

    private UUID episodeId;

    @NotNull
    private Long progressSeconds;

    @NotNull
    private Long durationSeconds;
}