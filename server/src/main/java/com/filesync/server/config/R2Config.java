package com.filesync.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import java.net.URI;

@Configuration
@ConditionalOnProperty(name = "storage.type", havingValue = "r2")
public class R2Config {

    private static final Logger log = LoggerFactory.getLogger(R2Config.class);
    public R2Config() {
        log.info("=== R2Config loaded ===");
    }

    @Value("${r2.endpoint}")
    private String endPoint;

    @Value("${r2.access-key-id}")
    private String accessKeyId;

    @Value("${r2.secret-access-key}")
    private String secretAccessKey;

    @Value("${r2.region:auto}")
    private String region;

    @Bean
    public S3Client r2S3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(endPoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                ))
                .build();
    }
}
