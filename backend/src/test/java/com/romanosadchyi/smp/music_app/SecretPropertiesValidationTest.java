package com.romanosadchyi.smp.music_app;

import com.romanosadchyi.smp.music_app.shared.security.JwtProperties;
import com.romanosadchyi.smp.music_app.shared.util.EncryptionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code @Validated} configuration properties of every module refuse blank secrets at startup.
 */
class SecretPropertiesValidationTest {

    final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesScan.class)
            .withPropertyValues(
                    "jwt.secret=0a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f9",
                    "jwt.expiration=60000",
                    "encryption.secret-key=abcdefghijabcdefghijabcdefghij12",
                    "minio.url=http://minio:9000",
                    "minio.public-url=http://localhost:9000",
                    "minio.region=us-east-1",
                    "minio.access-key=access",
                    "minio.secret-key=secret",
                    "minio.bucket-name=music-files",
                    "minio.presigned-url-expiry-seconds=3600");

    @Test
    void allSet_Binds() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JwtProperties.class).expiration()).isEqualTo(60_000);
            assertThat(context.getBean(EncryptionProperties.class).secretKey()).hasSize(32);
        });
    }

    @Test
    void blankJwtSecret_FailsStartup() {
        assertFailsOn("jwt.secret=", "jwt");
    }

    @Test
    void blankEncryptionKey_FailsStartup() {
        assertFailsOn("encryption.secret-key=", "encryption");
    }

    @Test
    void blankMinioSecret_FailsStartup() {
        assertFailsOn("minio.secret-key=", "minio");
    }

    private void assertFailsOn(String blankProperty, String prefix) {
        contextRunner.withPropertyValues(blankProperty).run(context -> assertThat(context)
                .getFailure()
                .rootCause()
                .hasMessageContaining(prefix));
    }

    @Configuration(proxyBeanMethods = false)
    @ConfigurationPropertiesScan
    static class PropertiesScan {
    }
}
