package com.Netflix.Streaming.Service;



import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Slf4j
public class HlsTranscoderService {

    @Value("${app.video.storage-path}")
    private String videoStoragePath;

    @Value("${app.video.ffmpeg-path}")
    private String ffmpegPath;

    /**
     * Executes FFmpeg in a background thread to convert raw MP4 to HLS.
     */
    @Async
    public void transcodeToHlsAsync(File rawMp4File, String titleFolder) {
        log.info("🎬 [Transcoder] Starting background HLS transcoding for folder: {}", titleFolder);
        long startTime = System.currentTimeMillis();

        try {
            // 1. Ensure target directory exists: /netflix-media/{titleFolder}/
            Path targetDirPath = Path.of(videoStoragePath, titleFolder);
            Files.createDirectories(targetDirPath);

            File masterPlaylist = targetDirPath.resolve("master.m3u8").toFile();

            // 2. Build FFmpeg command
            // -hls_time 10 = 10 second video chunks
            // -hls_playlist_type vod = Video On Demand
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

            processBuilder.redirectErrorStream(true); // Merge error and output streams
            Process process = processBuilder.start();

            // Read logs from FFmpeg process
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Suppress verbose FFmpeg output, log only errors or key milestones
                    if (line.contains("Error") || line.contains("Opening")) {
                        log.debug("FFmpeg: {}", line);
                    }
                }
            }

            int exitCode = process.waitFor();
            long durationSec = (System.currentTimeMillis() - startTime) / 1000;

            if (exitCode == 0) {
                log.info("✅ [Transcoder] SUCCESS! HLS Transcoding complete for '{}' in {}s", titleFolder, durationSec);
            } else {
                log.error("❌ [Transcoder] FFmpeg failed with exit code: {}", exitCode);
            }

        } catch (Exception e) {
            log.error("❌ [Transcoder] Exception during HLS transcoding for: {}", titleFolder, e);
        } finally {
            // 3. Clean up raw temporary MP4 file
            if (rawMp4File.exists()) {
                boolean deleted = rawMp4File.delete();
                log.info("🧹 [Transcoder] Temporary file cleaned up: {}", deleted);
            }
        }
    }
}