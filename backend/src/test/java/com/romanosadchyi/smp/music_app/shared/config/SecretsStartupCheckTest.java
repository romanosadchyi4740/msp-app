package com.romanosadchyi.smp.music_app.shared.config;

import com.romanosadchyi.smp.music_app.MusicAppApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretsStartupCheckTest {

    final SecretsStartupCheck check = new SecretsStartupCheck();

    @Test
    void localProfile_CommittedValues_AreAllowed() {
        MockEnvironment environment = withSecrets("12345678901234567890123456789012", "postgres");
        environment.setActiveProfiles("local");

        assertThatCode(() -> check.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
    }

    @Test
    void prodProfile_NewSecrets_Starts() {
        MockEnvironment environment = withSecrets("abcdefghijabcdefghijabcdefghij12", "a-real-db-password");
        environment.setActiveProfiles("prod");

        assertThatCode(() -> check.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
    }

    @Test
    void prodProfile_CommittedValues_FailNamingThePropertiesNotTheValues() {
        MockEnvironment environment = withSecrets("test-encryption-key-32-chars-ok!", "postgres");
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> check.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing: none")
                .hasMessageContaining("committed to the repository: encryption.secret-key, spring.datasource.password")
                .hasMessageNotContaining("test-encryption-key");
    }

    @Test
    void prodProfile_UnresolvedPlaceholder_CountsAsMissing() {
        MockEnvironment environment = withSecrets("abcdefghijabcdefghijabcdefghij12", "a-real-db-password");
        environment.setProperty("jwt.secret", "${JWT_SECRET}");
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> check.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing: jwt.secret;");
    }

    @Test
    void prodProfile_StartedWithoutSecrets_FailsBeforeCreatingBeans() {
        SpringApplicationBuilder application = new SpringApplicationBuilder(MusicAppApplication.class)
                .profiles("prod")
                .web(WebApplicationType.NONE);

        assertThatThrownBy(application::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Secrets are not configured");
    }

    private static MockEnvironment withSecrets(String encryptionKey, String databasePassword) {
        return new MockEnvironment()
                .withProperty("jwt.secret", "0a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f9")
                .withProperty("encryption.secret-key", encryptionKey)
                .withProperty("minio.secret-key", "a-real-minio-secret")
                .withProperty("spring.datasource.password", databasePassword);
    }
}
