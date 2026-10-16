package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class MinioServiceIntegrationTest {

    @Autowired
    MinioService minioService;

    @Test
    void uploadedFile_IsReadableThroughPresignedUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "hello.txt", "text/plain", "hello storage".getBytes());

        minioService.uploadFile(file, "it/hello.txt");
        String url = minioService.generatePresignedUrl("it/hello.txt");

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("hello storage");
        }
    }

    @Test
    void deletedFile_IsNoLongerReadable() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "gone.txt", "text/plain", "bye".getBytes());
        minioService.uploadFile(file, "it/gone.txt");

        minioService.deleteFile("it/gone.txt");

        String url = minioService.generatePresignedUrl("it/gone.txt");
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<Void> response = client.send(HttpRequest.newBuilder(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isEqualTo(404);
        }
    }
}
