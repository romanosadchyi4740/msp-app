package com.romanosadchyi.smp.music_app.storage.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param publicUrl the address browsers use for presigned URLs; differs from {@code url} when the backend runs in
 *                  Docker
 */
@Validated
@ConfigurationProperties("minio")
record MinioProperties(
        @NotBlank String url,
        @NotBlank String publicUrl,
        @NotBlank String region,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotBlank String bucketName,
        @Positive int presignedUrlExpirySeconds
) {
}
