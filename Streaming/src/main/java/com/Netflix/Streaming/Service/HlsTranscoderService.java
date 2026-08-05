package com.Netflix.Streaming.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class HlsTranscoderService {

    private final S3Service s3Service; // Inject S3 Service

    @Value("${app.video.storage-path:/tmp/netflix-transcode}")
    private String videoStoragePath;

    @Value("${app.video.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Async
    public void transcodeToHlsAsync(File rawMp4File, String titleFolder) {
        log.info("🎬 [Transcoder] Starting background HLS transcoding for: {}", titleFolder);
        long startTime = System.currentTimeMillis();
        Path targetDirPath = Path.of(videoStoragePath, titleFolder);

        try {
            // 1. Ensure temp target directory exists
            Files.createDirectories(targetDirPath);
            File masterPlaylist = targetDirPath.resolve("master.m3u8").toFile();

            // 2. Execute FFmpeg
            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffmpegPath,
                    "-i", rawMp4File.getAbsolutePath(),
                    "-codec:v", "libx264",
                    "-codec:a", "aac",
                    "-hls_time", "10",
                    "-hls_playlist_type", "vod",
                    "-hls_segment_filename", targetDirPath.resolve("segment_%03d.ts").toString(),
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
            long durationSec = (System.currentTimeMillis() - startTime) / 1000;

            if (exitCode == 0) {
                log.info("✅ [Transcoder] FFmpeg succeeded in {}s. Uploading HLS files to AWS S3...", durationSec);

                // 3. Upload all generated HLS files (.m3u8 and .ts) to AWS S3
                try (Stream<Path> walk = Files.walk(targetDirPath)) {
                    walk.filter(Files::isRegularFile).forEach(filePath -> {
                        String fileName = filePath.getFileName().toString();
                        String s3Folder = "movies/" + titleFolder;

                        try {
                            // Upload each file to S3
                            s3Service.uploadFileDirect(s3Folder, fileName, filePath.toFile());
                            log.info("Uploaded to S3: {}/{}", s3Folder, fileName);
                        } catch (Exception e) {
                            log.error("Failed to upload {} to S3", fileName, e);
                        }
                    });
                }

                log.info("🚀 [Transcoder] All HLS segments successfully pushed to S3 for '{}'", titleFolder);
            } else {
                log.error("❌ [Transcoder] FFmpeg failed with exit code: {}", exitCode);
            }

        } catch (Exception e) {
            log.error("❌ [Transcoder] Exception during transcoding for: {}", titleFolder, e);
        } finally {
            // 4. Clean up local temporary files (.mp4 and converted folder)
            cleanUpLocalTempFiles(rawMp4File, targetDirPath);
        }
    }

    private void cleanUpLocalTempFiles(File rawMp4File, Path targetDirPath) {
        if (rawMp4File.exists()) {
            rawMp4File.delete();
        }
        try (Stream<Path> walk = Files.walk(targetDirPath)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            log.info("🧹 [Transcoder] Cleaned up local temp folder: {}", targetDirPath);
        } catch (Exception e) {
            log.warn("Failed to clean temp folder", e);
        }
    }
}