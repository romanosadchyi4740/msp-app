package com.romanosadchyi.smp.music_app.service;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MinioServiceTest {

    @Mock
    MinioClient minioClient;

    @InjectMocks
    MinioService minioService;

    @Test
    void init_BucketDoesNotExist_CreatesBucket() throws Exception {
        ReflectionTestUtils.setField(minioService, "bucketName", "test-bucket");
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        minioService.init();

        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void init_BucketExists_DoesNotCreateBucket() throws Exception {
        ReflectionTestUtils.setField(minioService, "bucketName", "test-bucket");
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        minioService.init();

        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void uploadFile_Success() throws Exception {
        ReflectionTestUtils.setField(minioService, "bucketName", "test-bucket");
        MockMultipartFile file = new MockMultipartFile("file", "test.mp3", "audio/mpeg", "test data".getBytes());

        minioService.uploadFile(file, "objectName");

        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void generatePresignedUrl_Success() throws Exception {
        ReflectionTestUtils.setField(minioService, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(minioService, "presignedUrlExpirySeconds", 3600);
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("http://presigned-url");

        String url = minioService.generatePresignedUrl("objectName");

        assertEquals("http://presigned-url", url);
    }

    @Test
    void deleteFile_ValidName_Success() throws Exception {
        ReflectionTestUtils.setField(minioService, "bucketName", "test-bucket");

        minioService.deleteFile("objectName");

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void deleteFile_NullName_DoesNothing() throws Exception {
        minioService.deleteFile(null);
        verify(minioClient, never()).removeObject(any());
    }

    @Test
    void deleteFile_PendingName_DoesNothing() throws Exception {
        minioService.deleteFile("PENDING");
        verify(minioClient, never()).removeObject(any());
    }
}
