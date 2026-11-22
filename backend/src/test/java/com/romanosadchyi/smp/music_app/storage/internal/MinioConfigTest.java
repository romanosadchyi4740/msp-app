package com.romanosadchyi.smp.music_app.storage.internal;

import io.minio.credentials.IamAwsProvider;
import io.minio.credentials.Provider;
import io.minio.credentials.StaticProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MinioConfigTest {

    @Test
    void minioCredentials_KeysSet_UsesThemAsStaticCredentials() {
        Provider provider = new MinioConfig(properties("access", "secret")).minioCredentials();

        assertThat(provider).isInstanceOf(StaticProvider.class);
        assertThat(provider.fetch().accessKey()).isEqualTo("access");
        assertThat(provider.fetch().secretKey()).isEqualTo("secret");
    }

    @Test
    void minioCredentials_BothKeysEmpty_UsesTheInstanceRole() {
        assertThat(new MinioConfig(properties("", "")).minioCredentials()).isInstanceOf(IamAwsProvider.class);
        assertThat(new MinioConfig(properties(null, null)).minioCredentials()).isInstanceOf(IamAwsProvider.class);
    }

    @Test
    void credentialsComplete_OnlyOneKeySet_IsFalse() {
        assertThat(properties("access", "").isCredentialsComplete()).isFalse();
        assertThat(properties(null, "secret").isCredentialsComplete()).isFalse();
        assertThat(properties("access", "secret").isCredentialsComplete()).isTrue();
        assertThat(properties(" ", "").isCredentialsComplete()).isTrue();
    }

    private static MinioProperties properties(String accessKey, String secretKey) {
        return new MinioProperties("https://s3.eu-central-1.amazonaws.com", "https://s3.eu-central-1.amazonaws.com",
                "eu-central-1", accessKey, secretKey, "uploads", 3600);
    }
}
