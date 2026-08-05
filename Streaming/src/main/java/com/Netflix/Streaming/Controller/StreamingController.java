package com.Netflix.Streaming.Controller;

import com.Netflix.Streaming.DTO.Request.ProgressUpdateRequest;
import com.Netflix.Streaming.DTO.Response.WatchProgressResponse;
import com.Netflix.Streaming.Service.HlsTranscoderService;
import com.Netflix.Streaming.Service.S3Service;
import com.Netflix.Streaming.Service.StreamAccessService; // 👈 Added
import com.Netflix.Streaming.Service.StreamingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/stream")
@RequiredArgsConstructor
public class StreamingController {

    private final StreamingService streamingService;
    private final StreamAccessService streamAccessService; // 👈 1. Inject StreamAccessService

    private final HlsTranscoderService hlsTranscoderService;
    private final S3Service s3Service;


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


    @GetMapping("/video/{movieKey}")
    public ResponseEntity<StreamingResponseBody> streamVideo(
            @PathVariable String movieKey,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {

        String fullS3Key = "movies/" + movieKey;

        // 1. Fetch total video file size from S3
        HeadObjectResponse metadata = s3Service.getObjectMetadata(fullS3Key);
        long fileSize = metadata.contentLength();

        // 2. Calculate byte range boundaries (2MB default chunk size)
        long rangeStart = 0;
        long rangeEnd = fileSize - 1;

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String[] ranges = rangeHeader.replace("bytes=", "").split("-");
            rangeStart = Long.parseLong(ranges[0]);
            if (ranges.length > 1 && !ranges[1].isEmpty()) {
                rangeEnd = Long.parseLong(ranges[1]);
            } else {
                // 2 MB chunk size cap
                rangeEnd = Math.min(rangeStart + (2 * 1024 * 1024), fileSize - 1);
            }
        }

        long contentLength = (rangeEnd - rangeStart) + 1;

        // 3. Request specific byte slice from AWS S3
        ResponseInputStream<GetObjectResponse> s3InputStream = s3Service.getObjectRange(fullS3Key, rangeStart, rangeEnd);

        // 4. Stream response body directly to the HTTP output stream
        StreamingResponseBody responseBody = outputStream -> {
            byte[] buffer = new byte[8192];
            int bytesRead;
            try (InputStream in = s3InputStream) {
                while ((bytesRead = in.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        };

        // 5. Construct HTTP 206 Partial Content Response Headers
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "video/mp4");
        headers.add("Accept-Ranges", "bytes");
        headers.add("Content-Range", String.format("bytes %d-%d/%d", rangeStart, rangeEnd, fileSize));
        headers.setContentLength(contentLength);

        log.info("Streaming video slice [{}] - Bytes {}-{}/{}", movieKey, rangeStart, rangeEnd, fileSize);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .headers(headers)
                .body(responseBody);
    }
}