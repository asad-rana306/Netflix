package com.Netfilx.User.Service;

<<<<<<< HEAD
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
=======
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
>>>>>>> 08f2502 (attached S3 and successfully streamed on local)
public class S3Service {

    private final S3Client s3Client;

<<<<<<< HEAD
    @Value("${aws.s3.bucket}")
    private String bucketName;

    /**
     * 1. Uploads binary files (profile pictures, thumbnails, or raw video files) to S3.
     * @return String - The S3 object key stored in PostgreSQL (e.g., "movies/sample.mp4")
     */
    public String uploadFile(String folder, String fileName, MultipartFile file) throws IOException {
        String key = folder + "/" + fileName;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
=======
    @Value("${aws.s3.bucket:netflix-clone-media-bucket}")
    private String bucketName;

    @Value("${aws.region:us-east-1}")
    private String region;

    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public String uploadFile(MultipartFile file, String folder) throws IOException {
        String fileName = folder + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
>>>>>>> 08f2502 (attached S3 and successfully streamed on local)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
<<<<<<< HEAD
        log.info("Successfully uploaded file to S3 bucket [{}]: {}", bucketName, key);

        return key;
    }

    /**
     * 2. Inspects S3 object metadata to get total file size without downloading content.
     */
    public HeadObjectResponse getObjectMetadata(String key) {
        HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        return s3Client.headObject(headObjectRequest);
    }

    /**
     * 3. Fetches a specific byte range slice from S3 for video chunk streaming (HTTP 206).
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
     * 4. Deletes an object from S3.
     */
    public void deleteFile(String key) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3Client.deleteObject(deleteObjectRequest);
        log.info("Deleted S3 object [{}] from bucket [{}]", key, bucketName);
=======

        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, fileName);
>>>>>>> 08f2502 (attached S3 and successfully streamed on local)
    }
}