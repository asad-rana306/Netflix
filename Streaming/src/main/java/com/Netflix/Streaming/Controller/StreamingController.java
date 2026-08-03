package com.Netflix.Streaming.Controller;

import com.Netflix.Streaming.DTO.Request.ProgressUpdateRequest;
import com.Netflix.Streaming.DTO.Response.WatchProgressResponse;
import com.Netflix.Streaming.Service.HlsTranscoderService;
import com.Netflix.Streaming.Service.StreamAccessService; // 👈 Added
import com.Netflix.Streaming.Service.StreamingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stream")
@RequiredArgsConstructor
public class StreamingController {

    private final StreamingService streamingService;
    private final StreamAccessService streamAccessService; // 👈 1. Inject StreamAccessService

    private final HlsTranscoderService hlsTranscoderService;

    @Value("${app.video.temp-path}")
    private String tempPath;

    /**
     * Admin Upload Endpoint: Accepts a raw MP4 and triggers background HLS transcoding.
     * Returns 202 ACCEPTED immediately to prevent gateway timeouts.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadAndTranscodeVideo(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam("titleFolder") String titleFolder) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File cannot be empty.");
        }

        try {
            // 1. Create temp directory if it doesn't exist
            File tempDir = new File(tempPath);
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }

            // 2. Save raw MP4 file temporarily to disk
            File tempFile = new File(tempDir, titleFolder + "_" + System.currentTimeMillis() + ".mp4");
            file.transferTo(tempFile);

            // 3. Trigger Async Transcoding Job (Non-blocking)
            hlsTranscoderService.transcodeToHlsAsync(tempFile, titleFolder);

            // 4. Return 202 Accepted immediately
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body("Video upload accepted. HLS transcoding in progress for folder: " + titleFolder);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to stage video file for transcoding: " + e.getMessage());
        }
    }

    @GetMapping("/hls/{titleFolder}/{file}")
    public ResponseEntity<?> streamHls(
            @PathVariable String titleFolder,
            @PathVariable String file,
            @RequestHeader(value = "X-User-Id", required = false) String userId) throws FileNotFoundException {

        // 🔒 2. Gatekeeping check for HLS streaming
        if (!streamAccessService.canUserStream(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: An active subscription is required to watch this content.");
        }

        Resource resource = streamingService.loadHlsResource(titleFolder, file);

        HttpHeaders headers = new HttpHeaders();

        // 💡 Set the specific MIME type required for HLS playback
        if (file.endsWith(".m3u8")) {
            headers.setContentType(MediaType.parseMediaType("application/x-mpegURL"));
        } else if (file.endsWith(".ts")) {
            headers.setContentType(MediaType.parseMediaType("video/mp2t"));
        } else {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }

        // Return standard 200 OK with the full small file
        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }

    /**
     * Serves video files using HTTP 206 Partial Content for HTML5 player seeking.
     */
    @GetMapping("/video/{fileName}")
    public ResponseEntity<?> streamVideo(
            @PathVariable String fileName,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userId) throws IOException {

        // 🔒 3. Gatekeeping check for raw MP4 streaming
        if (!streamAccessService.canUserStream(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: An active subscription is required to watch this content.");
        }

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