package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.MinioGetPresignedObjectUrlException;
import com.romanosadchyi.smp.music_app.exception.MinioUploadException;
import io.minio.*;
import io.minio.Http.Method;
import io.minio.errors.MinioException;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MinioService {

    MinioClient minioClient;

    @NonFinal
    @Value("${minio.bucket-name}")
    String bucketName;

    @NonFinal
    @Value("${minio.presigned-url-expiry-seconds}")
    int presignedUrlExpirySeconds;

    @PostConstruct
    public void init() throws MinioException {
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    public void uploadFile(MultipartFile file, String objectName) throws MinioUploadException {
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1L)
                            .contentType(file.getContentType())
                            .build()
            );

            log.info("File uploaded successfully to MinIO: {}", objectName);
        } catch (Exception e) {
            log.error("Error occurred while uploading file to MinIO", e);
            throw new MinioUploadException("Error occurred while uploading file to MinIO", e);
        }
    }

    public String generatePresignedUrl(String fileKey) throws MinioGetPresignedObjectUrlException {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(fileKey)
                            .expiry(presignedUrlExpirySeconds)
                            .build()
            );
        } catch (MinioException e) {
            log.error("Error occurred while generating presigned URL", e);
            throw new MinioGetPresignedObjectUrlException(e);
        } catch (Exception e) {
            log.error("Unexpected error occurred while generating presigned URL", e);
            throw new RuntimeException(e);
        }
    }

}
