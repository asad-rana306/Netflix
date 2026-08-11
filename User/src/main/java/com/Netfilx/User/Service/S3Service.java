package com.Netfilx.User.Service;

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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket:netflix-clone-media-bucket}")
    private String bucketName;

    @Value("${aws.region:us-east-1}")
    private String region;

    public String uploadFile(String folder, MultipartFile file) throws IOException {
        String fileName = folder + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        log.info("Successfully uploaded file to S3 bucket [{}]: {}", bucketName, fileName);

        return fileName; // This is the S3 Key to store in DB
    }

    public HeadObjectResponse getObjectMetadata(String key) {
        HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        return s3Client.headObject(headObjectRequest);
    }

    public ResponseInputStream<GetObjectResponse> getObjectRange(String key, long start, long end) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .range(String.format("bytes=%d-%d", start, end))
                .build();

        return s3Client.getObject(getObjectRequest);
    }

    public void deleteFile(String key) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3Client.deleteObject(deleteObjectRequest);
        log.info("Deleted S3 object [{}] from bucket [{}]", key, bucketName);
    }
}