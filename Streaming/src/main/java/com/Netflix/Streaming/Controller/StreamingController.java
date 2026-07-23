package com.Netflix.Streaming.Controller;

import com.Netflix.Streaming.DTO.Request.ProgressUpdateRequest;
import com.Netflix.Streaming.DTO.Response.WatchProgressResponse;
import com.Netflix.Streaming.Service.StreamingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stream")
@RequiredArgsConstructor
public class StreamingController {

    private final StreamingService streamingService;

    /**
     * Serves video files using HTTP 206 Partial Content for HTML5 player seeking.
     */
    @GetMapping("/video/{fileName}")
    public ResponseEntity<ResourceRegion> streamVideo(
            @PathVariable String fileName,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) throws IOException {

        HttpRange range = null;
        if (rangeHeader != null && !rangeHeader.isEmpty()) {
            range = HttpRange.parseRanges(rangeHeader).get(0);
        }

        ResourceRegion region = streamingService.prepareVideoRegion(fileName, range);

        // 🎬 Determine media type dynamically or default explicitly to video/mp4
        MediaType mediaType = fileName.endsWith(".mp4")
                ? MediaType.parseMediaType("video/mp4")
                : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(mediaType) // 👈 MUST BE video/mp4 for Chrome to play
                .header(HttpHeaders.ACCEPT_RANGES, "bytes") // 👈 Enables seeking & smooth playback
                .body(region);
    }

    /**
     * Heartbeat API sent by React player every 5-10 seconds to track watch progress.
     */
    @PostMapping("/progress")
    public ResponseEntity<WatchProgressResponse> updateWatchProgress(
            @Valid @RequestBody ProgressUpdateRequest request) {
        return ResponseEntity.ok(streamingService.updateProgress(request));
    }

    /**
     * API to populate the "Continue Watching" row on the Netflix Home Page.
     */
    @GetMapping("/continue-watching/{profileId}")
    public ResponseEntity<List<WatchProgressResponse>> getContinueWatching(
            @PathVariable UUID profileId) {
        return ResponseEntity.ok(streamingService.getContinueWatching(profileId));
    }
}