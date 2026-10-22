package com.romanosadchyi.smp.music_app.storage.internal;

import io.minio.MinioClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class MinioConfig {

    MinioProperties properties;

    @Bean
    @Primary
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(properties.url())
                .region(properties.region())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    /**
     * Signs presigned URLs for the address browsers use, which differs from {@code minio.url} when the backend
     * runs in Docker. Signing is offline; the fixed region stops the client from looking it up over the network.
     */
    @Bean
    public MinioClient minioPresignClient() {
        return MinioClient.builder()
                .endpoint(properties.publicUrl())
                .region(properties.region())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }
}
