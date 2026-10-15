package com.romanosadchyi.smp.music_app.config;

import io.minio.MinioClient;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MinioConfig {

    @Value("${minio.url}")
    String minioUrl;

    @Value("${minio.public-url}")
    String minioPublicUrl;

    @Value("${minio.region}")
    String region;

    @Value("${minio.access-key}")
    String accessKey;

    @Value("${minio.secret-key}")
    String secretKey;

    @Bean
    @Primary
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(minioUrl)
                .region(region)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * Signs presigned URLs for the address browsers use, which differs from {@code minio.url} when the backend
     * runs in Docker. Signing is offline; the fixed region stops the client from looking it up over the network.
     */
    @Bean
    public MinioClient minioPresignClient() {
        return MinioClient.builder()
                .endpoint(minioPublicUrl)
                .region(region)
                .credentials(accessKey, secretKey)
                .build();
    }

}
