package com.Netflix.Streaming.DTO.Response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class WatchProgressResponse {
    private UUID id;
    private UUID profileId;
    private Long titleId;
    private Long episodeId;
    private Long progressSeconds;
    private Long durationSeconds;
    private boolean isCompleted;
    private LocalDateTime lastWatchedAt;
}