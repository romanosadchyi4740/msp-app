package com.romanosadchyi.smp.music_app.storage.internal;

import io.minio.MinioClient;
import io.minio.credentials.IamAwsProvider;
import io.minio.credentials.Provider;
import io.minio.credentials.StaticProvider;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class MinioConfig {

    MinioProperties properties;

    /**
     * Static keys (MinIO locally), or, when both keys are empty, the EC2 instance role read from the instance
     * metadata (IMDSv2) and refreshed before it expires, so no AWS access keys exist for the app.
     */
    @Bean
    public Provider minioCredentials() {
        if (properties.usesInstanceRole()) {
            log.info("S3 credentials come from the EC2 instance role");
            return new IamAwsProvider(null, null);
        }
        return new StaticProvider(properties.accessKey(), properties.secretKey(), null);
    }

    @Bean
    @Primary
    public MinioClient minioClient(Provider minioCredentials) {
        return MinioClient.builder()
                .endpoint(properties.url())
                .region(properties.region())
                .credentialsProvider(minioCredentials)
                .build();
    }

    /**
     * Signs presigned URLs for the address browsers use, which differs from {@code minio.url} when the backend
     * runs in Docker. Signing is offline; the fixed region stops the client from looking it up over the network.
     */
    @Bean
    public MinioClient minioPresignClient(Provider minioCredentials) {
        return MinioClient.builder()
                .endpoint(properties.publicUrl())
                .region(properties.region())
                .credentialsProvider(minioCredentials)
                .build();
    }
}
