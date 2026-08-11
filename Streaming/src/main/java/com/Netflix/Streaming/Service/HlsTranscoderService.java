package com.Netflix.Streaming.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Slf4j
@RequiredArgsConstructor
public class HlsTranscoderService {

    private final S3Client s3Client;

    @Value("${app.video.temp-path:/tmp/netflix-media/temp/}")
    private String tempPath;

    @Value("${app.video.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Value("${aws.s3.bucket-name:netflix-clone-media-bucket}")
    private String bucketName;

    @Async
    public void transcodeToHlsAsync(File rawMp4File, String titleFolder) {
        log.info("🎬 [Transcoder] Starting HLS transcoding for: {}", titleFolder);
        long startTime = System.currentTimeMillis();

        // Temporary local directory for generated HLS files
        Path localOutputDir = Path.of(tempPath, "hls-output", titleFolder);

        try {
            Files.createDirectories(localOutputDir);
            File masterPlaylist = localOutputDir.resolve("master.m3u8").toFile();

            // 1. Run FFmpeg locally to create HLS files
            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffmpegPath,
                    "-i", rawMp4File.getAbsolutePath(),
                    "-codec:v", "libx264",
                    "-codec:a", "aac",
                    "-hls_time", "10",
                    "-hls_playlist_type", "vod",
                    "-hls_segment_filename", localOutputDir.resolve("segment_%03d.ts").toString(),
                    "-start_number", "0",
                    masterPlaylist.getAbsolutePath()
            );

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("Error") || line.contains("Opening")) {
                        log.debug("FFmpeg: {}", line);
                    }
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("✅ FFmpeg conversion complete. Uploading HLS files to AWS S3...");

                // 2. Upload all generated .m3u8 and .ts files to S3
                uploadHlsFolderToS3(localOutputDir, titleFolder);

                long durationSec = (System.currentTimeMillis() - startTime) / 1000;
                log.info("🚀 [Transcoder] SUCCESS! Uploaded to S3 folder '{}' in {}s", titleFolder, durationSec);
            } else {
                log.error("❌ FFmpeg failed with exit code: {}", exitCode);
            }

        } catch (Exception e) {
            log.error("❌ Exception during transcoding/uploading for: {}", titleFolder, e);
        } finally {
            // 3. Clean up local raw file and local HLS temporary folder
            cleanupLocalFiles(rawMp4File, localOutputDir);
        }
    }

    private void uploadHlsFolderToS3(Path localFolder, String titleFolder) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(localFolder)) {
            for (Path filePath : stream) {
                if (Files.isRegularFile(filePath)) {
                    String fileName = filePath.getFileName().toString();
                    String s3Key = titleFolder + "/" + fileName;

                    // Set Content-Type header so browser players recognize HLS formats correctly
                    String contentType = fileName.endsWith(".m3u8")
                            ? "application/x-mpegURL"
                            : "video/MP2T";

                    PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(s3Key)
                            .contentType(contentType)
                            .build();

                    s3Client.putObject(putObjectRequest, RequestBody.fromFile(filePath.toFile()));
                    log.info("Uploaded to S3: {}", s3Key);
                }
            }
        } catch (Exception e) {
            log.error("Failed to upload HLS files to S3", e);
        }
    }

    private void cleanupLocalFiles(File rawMp4File, Path localOutputDir) {
        if (rawMp4File.exists()) {
            rawMp4File.delete();
        }
        try {
            if (Files.exists(localOutputDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(localOutputDir)) {
                    for (Path file : stream) {
                        Files.deleteIfExists(file);
                    }
                }
                Files.deleteIfExists(localOutputDir);
            }
            log.info("🧹 Cleaned up local temporary files.");
        } catch (Exception e) {
            log.error("Failed to clean up local temp files", e);
        }
    }
}