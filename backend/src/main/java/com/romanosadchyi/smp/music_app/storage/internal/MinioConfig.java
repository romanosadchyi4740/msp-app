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

import java.time.Clock;
import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class MinioConfig {

    static final Duration REFRESH_MARGIN = Duration.ofMinutes(5);

    MinioProperties properties;

    /**
     * Static keys (MinIO locally), or, when both keys are empty, the EC2 instance role read from the instance
     * metadata (IMDSv2), so no AWS access keys exist for the app. Role credentials are refreshed while they still
     * outlive a presigned URL signed with them, plus a margin.
     */
    @Bean
    public Provider minioCredentials() {
        if (properties.usesInstanceRole()) {
            log.info("S3 credentials come from the EC2 instance role");
            Duration minRemaining = Duration.ofSeconds(properties.presignedUrlExpirySeconds()).plus(REFRESH_MARGIN);
            return new RefreshingCredentialsProvider(() -> new IamAwsProvider(null, null), minRemaining,
                    Clock.systemUTC());
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
     * runs in Docker. Signing needs no network call (the fixed region stops the client from looking it up), except
     * the instance-role credential refresh.
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
