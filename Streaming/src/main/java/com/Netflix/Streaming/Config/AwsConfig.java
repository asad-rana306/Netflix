package com.Netflix.Streaming.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AwsConfig {

    @Value("${aws.access-key:${aws.s3.access-key:dummy_access_key}}")
    private String accessKey;

    @Value("${aws.secret-key:${aws.s3.secret-key:dummy_secret_key}}")
    private String secretKey;

    @Value("${aws.region:${aws.s3.region:us-east-1}}")
    private String region;

    @Bean
    public S3Client s3Client() {
        String cleanAccessKey = (accessKey != null && !accessKey.isBlank()) ? accessKey : "dummy_access_key";
        String cleanSecretKey = (secretKey != null && !secretKey.isBlank()) ? secretKey : "dummy_secret_key";
        String cleanRegion = (region != null && !region.isBlank()) ? region : "us-east-1";

        return S3Client.builder()
                .region(Region.of(cleanRegion))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(cleanAccessKey, cleanSecretKey)))
                .build();
    }
}