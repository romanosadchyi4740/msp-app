package com.romanosadchyi.smp.music_app.storage.internal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param publicUrl the address browsers use for presigned URLs; differs from {@code url} when the backend runs in
 *                  Docker
 * @param accessKey static access key; empty together with {@code secretKey} on AWS, where the credentials come from
 *                  the EC2 instance role
 */
@Validated
@ConfigurationProperties("minio")
record MinioProperties(
        @NotBlank String url,
        @NotBlank String publicUrl,
        @NotBlank String region,
        String accessKey,
        String secretKey,
        @NotBlank String bucketName,
        @Positive int presignedUrlExpirySeconds
) {

    boolean usesInstanceRole() {
        return isBlank(accessKey) && isBlank(secretKey);
    }

    @AssertTrue(message = "minio.access-key and minio.secret-key must both be set, or both be empty to use the "
            + "instance role")
    boolean isCredentialsComplete() {
        return isBlank(accessKey) == isBlank(secretKey);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
