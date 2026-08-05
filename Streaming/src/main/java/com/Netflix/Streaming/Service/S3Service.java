package com.Netflix.Streaming.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    /**
     * 1. Uploads Spring MultipartFile (from REST Controllers) to S3.
     */
    public String uploadFile(String folder, String fileName, MultipartFile file) throws IOException {
        String key = folder + "/" + fileName;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        log.info("Successfully uploaded file to S3 bucket [{}]: {}", bucketName, key);

        return key;
    }

    /**
     * 2. Uploads local java.io.File directly to S3 (used by FFmpeg Transcoder).
     */
    public String uploadFileDirect(String folder, String fileName, File file) {
        String key = folder + "/" + fileName;

        String contentType = "application/octet-stream";
        if (fileName.endsWith(".m3u8")) {
            contentType = "application/x-mpegURL";
        } else if (fileName.endsWith(".ts")) {
            contentType = "video/MP2T";
        }

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));
        log.info("Successfully uploaded HLS segment to S3 [{}]: {}", bucketName, key);

        return key;
    }

    /**
     * 3. Inspects S3 object metadata to get total file size without downloading content.
     */
    public HeadObjectResponse getObjectMetadata(String key) {
        HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        return s3Client.headObject(headObjectRequest);
    }

    /**
     * 4. Fetches a specific byte range slice from S3 for video chunk streaming (HTTP 206).
     */
    public ResponseInputStream<GetObjectResponse> getObjectRange(String key, long start, long end) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .range(String.format("bytes=%d-%d", start, end))
                .build();

        return s3Client.getObject(getObjectRequest);
    }

    /**
     * 5. Deletes an object from S3.
     */
    public void deleteFile(String key) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3Client.deleteObject(deleteObjectRequest);
        log.info("Deleted S3 object [{}] from bucket [{}]", key, bucketName);
    }
}