package com.Netflix.Streaming.Service;

import com.Netflix.Streaming.DTO.Request.ProgressUpdateRequest;
import com.Netflix.Streaming.DTO.Response.WatchProgressResponse;
import com.Netflix.Streaming.Entity.WatchHistory;
import com.Netflix.Streaming.Repository.WatchHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpRange;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingService {

    private final WatchHistoryRepository watchHistoryRepository;

    @Value("${app.video.storage-path}")
    private String videoStoragePath;

    /**
     * Constructs a chunked ResourceRegion for HTTP 206 Partial Content Streaming.
     */
    public ResourceRegion prepareVideoRegion(String fileName, HttpRange range) throws IOException {
        File file = new File(videoStoragePath + fileName);
        if (!file.exists()) {
            throw new IllegalArgumentException("Video file not found: " + fileName);
        }

        Resource videoResource = new FileSystemResource(file);
        long contentLength = videoResource.contentLength();
        long chunkSize = 1024 * 1024; // 1MB Chunk size for instant seeking

        if (range != null) {
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            long rangeLength = Math.min(chunkSize, end - start + 1);
            return new ResourceRegion(videoResource, start, rangeLength);
        } else {
            long rangeLength = Math.min(chunkSize, contentLength);
            return new ResourceRegion(videoResource, 0, rangeLength);
        }
    }

    /**
     * Updates playback progress for a profile.
     */
    @Transactional
    public WatchProgressResponse updateProgress(ProgressUpdateRequest req) {
        Optional<WatchHistory> existing = (req.getEpisodeId() != null)
                ? watchHistoryRepository.findByProfileIdAndTitleIdAndEpisodeId(req.getProfileId(), req.getTitleId(), req.getEpisodeId())
                : watchHistoryRepository.findByProfileIdAndTitleIdAndEpisodeIdIsNull(req.getProfileId(), req.getTitleId());

        WatchHistory history = existing.orElseGet(() -> WatchHistory.builder()
                .profileId(req.getProfileId())
                .titleId(req.getTitleId())
                .episodeId(req.getEpisodeId())
                .build());

        history.setProgressSeconds(req.getProgressSeconds());
        history.setDurationSeconds(req.getDurationSeconds());

        WatchHistory saved = watchHistoryRepository.save(history);
        return mapToResponse(saved);
    }

    /**
     * Returns "Continue Watching" items for a given user profile.
     */
    public List<WatchProgressResponse> getContinueWatching(UUID profileId) {
        return watchHistoryRepository.findByProfileIdAndIsCompletedFalseOrderByLastWatchedAtDesc(profileId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private WatchProgressResponse mapToResponse(WatchHistory history) {
        return WatchProgressResponse.builder()
                .id(history.getId())
                .profileId(history.getProfileId())
                .titleId(history.getTitleId())
                .episodeId(history.getEpisodeId())
                .progressSeconds(history.getProgressSeconds())
                .durationSeconds(history.getDurationSeconds())
                .isCompleted(history.isCompleted())
                .lastWatchedAt(history.getLastWatchedAt())
                .build();
    }
}