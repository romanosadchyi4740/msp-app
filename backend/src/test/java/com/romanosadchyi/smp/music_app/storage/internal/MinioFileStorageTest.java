package com.romanosadchyi.smp.music_app.storage.internal;

import com.romanosadchyi.smp.music_app.storage.external.StorageException;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MinioFileStorageTest {

    @Mock
    MinioClient minioClient;

    @Mock
    MinioClient minioPresignClient;

    MinioFileStorage fileStorage;

    @BeforeEach
    void setUp() {
        fileStorage = new MinioFileStorage(minioClient, minioPresignClient, new MinioProperties("http://minio:9000",
                "http://localhost:9000", "us-east-1", "access", "secret", "test-bucket", 3600));
    }

    @Test
    void init_BucketDoesNotExist_CreatesBucket() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        fileStorage.init();

        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void init_BucketExists_DoesNotCreateBucket() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        fileStorage.init();

        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void upload_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.mp3", "audio/mpeg", "test data".getBytes());

        fileStorage.upload(file, "objectName");

        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void presignedGetUrl_SignsWithPresignClient() throws Exception {
        when(minioPresignClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("http://presigned-url");

        String url = fileStorage.presignedGetUrl("objectName");

        assertEquals("http://presigned-url", url);
        verify(minioClient, never()).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }

    @Test
    void delete_ValidName_Success() throws Exception {

        fileStorage.delete("objectName");

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void upload_ClientFails_ThrowsStorageException() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.mp3", "audio/mpeg", "test data".getBytes());
        when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(new IllegalStateException("down"));

        assertThrows(StorageException.class, () -> fileStorage.upload(file, "objectName"));
    }

    @Test
    void delete_ClientFails_DoesNotThrow() throws Exception {
        doThrow(new IllegalStateException("down")).when(minioClient).removeObject(any(RemoveObjectArgs.class));

        fileStorage.delete("objectName");
    }

    @Test
    void delete_NullName_DoesNothing() throws Exception {
        fileStorage.delete(null);
        verify(minioClient, never()).removeObject(any());
    }

    @Test
    void delete_PendingName_DoesNothing() throws Exception {
        fileStorage.delete("PENDING");
        verify(minioClient, never()).removeObject(any());
    }
}
