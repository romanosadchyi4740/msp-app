package com.romanosadchyi.smp.music_app;

import com.romanosadchyi.smp.music_app.rtc.internal.config.TurnProperties;
import com.romanosadchyi.smp.music_app.shared.security.JwtProperties;
import com.romanosadchyi.smp.music_app.shared.util.EncryptionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
                    "minio.presigned-url-expiry-seconds=3600",
                    "turn.secret=a-real-turn-secret",
                    "turn.urls=turn:turn.example.com:3478?transport=udp,turns:turn.example.com:443?transport=tcp",
                    "turn.stun-urls=",
                    "turn.credential-ttl-seconds=3600");

    @Test
    void allSet_Binds() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JwtProperties.class).expiration()).isEqualTo(60_000);
            assertThat(context.getBean(EncryptionProperties.class).secretKey()).hasSize(32);
            assertThat(context.getBean(TurnProperties.class).urls()).hasSize(2);
            assertThat(context.getBean(TurnProperties.class).stunUrls()).isEmpty();
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

    @Test
    void blankTurnSecret_FailsStartup() {
        assertFailsOn("turn.secret=", "turn");
    }

    @Test
    void noTurnUrls_FailsStartup() {
        assertFailsOn("turn.urls=", "turn");
    }

    @Test
    void turnUrlWithoutItsScheme_FailsStartup() {
        // One malformed entry would make every RTCPeerConnection in the browsers throw
        assertFailsOn("turn.urls=turn.example.com:3478", "turn");
        assertFailsOn("turn.urls=stun:turn.example.com:3478", "turn");
        assertFailsOn("turn.stun-urls=turn:turn.example.com:3478", "turn");
    }

    @Test
    void turnWithoutStunUrls_BindsAnEmptyList() {
        new ApplicationContextRunner()
                .withUserConfiguration(TurnOnly.class)
                .withPropertyValues("turn.secret=a-real-turn-secret", "turn.urls=turns:turn.example.com:443",
                        "turn.credential-ttl-seconds=3600")
                .run(context -> assertThat(context.getBean(TurnProperties.class).stunUrls()).isEmpty());
    }

    @Test
    void turnCredentialLifetimeOutsideOneToSixHours_FailsStartup() {
        assertFailsOn("turn.credential-ttl-seconds=600", "turn");
        assertFailsOn("turn.credential-ttl-seconds=86400", "turn");
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

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TurnProperties.class)
    static class TurnOnly {
    }
}
