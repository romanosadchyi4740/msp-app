package com.romanosadchyi.smp.music_app.storage.internal;

import com.romanosadchyi.smp.music_app.storage.external.FileStorage;
import com.romanosadchyi.smp.music_app.storage.external.StorageException;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http.Method;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * {@link FileStorage} on MinIO, or any S3-compatible store the MinIO client can reach.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class MinioFileStorage implements FileStorage {

    MinioClient minioClient;

    @Qualifier("minioPresignClient")
    MinioClient minioPresignClient;

    @NonFinal
    @Value("${minio.bucket-name}")
    String bucketName;

    @NonFinal
    @Value("${minio.presigned-url-expiry-seconds}")
    int presignedUrlExpirySeconds;

    @PostConstruct
    void init() throws MinioException {
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    @Override
    public void upload(MultipartFile file, String key) {
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(key)
                            .stream(inputStream, file.getSize(), -1L)
                            .contentType(file.getContentType())
                            .build()
            );

            log.info("File uploaded successfully to MinIO: {}", key);
        } catch (Exception e) {
            log.error("Error occurred while uploading file to MinIO", e);
            throw new StorageException("Error occurred while uploading file to MinIO", e);
        }
    }

    @Override
    public String presignedGetUrl(String key) {
        try {
            return minioPresignClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(key)
                            .expiry(presignedUrlExpirySeconds)
                            .build()
            );
        } catch (Exception e) {
            log.error("Error occurred while generating presigned URL", e);
            throw new StorageException("Error occurred while generating presigned URL", e);
        }
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank() || key.equals("PENDING")) {
            return;
        }
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(key)
                            .build()
            );
            log.info("File deleted successfully from MinIO: {}", key);
        } catch (Exception e) {
            log.error("Error occurred while deleting file from MinIO: {}", key, e);
        }
    }
}
