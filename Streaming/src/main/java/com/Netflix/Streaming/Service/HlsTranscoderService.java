package com.Netflix.Streaming.Service;

<<<<<<< HEAD
import lombok.RequiredArgsConstructor;
=======
>>>>>>> 08f2502 (attached S3 and successfully streamed on local)
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
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class HlsTranscoderService {

<<<<<<< HEAD
    private final S3Service s3Service; // Inject S3 Service

    @Value("${app.video.storage-path:/tmp/netflix-transcode}")
    private String videoStoragePath;
=======
    private final S3Client s3Client;

    @Value("${app.video.temp-path}")
    private String tempPath;
>>>>>>> 08f2502 (attached S3 and successfully streamed on local)

    @Value("${app.video.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

<<<<<<< HEAD
    @Async
    public void transcodeToHlsAsync(File rawMp4File, String titleFolder) {
        log.info("🎬 [Transcoder] Starting background HLS transcoding for: {}", titleFolder);
=======
    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public HlsTranscoderService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Async
    public void transcodeToHlsAsync(File rawMp4File, String titleFolder) {
        log.info("🎬 [Transcoder] Starting HLS transcoding for: {}", titleFolder);
>>>>>>> 08f2502 (attached S3 and successfully streamed on local)
        long startTime = System.currentTimeMillis();
        Path targetDirPath = Path.of(videoStoragePath, titleFolder);

        // Temporary local directory for generated HLS files
        Path localOutputDir = Path.of(tempPath, "hls-output", titleFolder);

        try {
<<<<<<< HEAD
            // 1. Ensure temp target directory exists
            Files.createDirectories(targetDirPath);
            File masterPlaylist = targetDirPath.resolve("master.m3u8").toFile();

            // 2. Execute FFmpeg
=======
            Files.createDirectories(localOutputDir);
            File masterPlaylist = localOutputDir.resolve("master.m3u8").toFile();

            // 1. Run FFmpeg locally to create HLS files
>>>>>>> 08f2502 (attached S3 and successfully streamed on local)
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
<<<<<<< HEAD
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
=======
                log.info("✅ FFmpeg conversion complete. Uploading HLS files to AWS S3...");

                // 2. Upload all generated .m3u8 and .ts files to S3
                uploadHlsFolderToS3(localOutputDir, titleFolder);

                long durationSec = (System.currentTimeMillis() - startTime) / 1000;
                log.info("🚀 [Transcoder] SUCCESS! Uploaded to S3 folder '{}' in {}s", titleFolder, durationSec);

>>>>>>> 08f2502 (attached S3 and successfully streamed on local)
            } else {
                log.error("❌ FFmpeg failed with exit code: {}", exitCode);
            }

        } catch (Exception e) {
<<<<<<< HEAD
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
=======
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
        // Delete raw temp video
        if (rawMp4File.exists()) {
            rawMp4File.delete();
        }
        // Delete generated local HLS files
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
>>>>>>> 08f2502 (attached S3 and successfully streamed on local)
        }
    }
}