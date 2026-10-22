package com.romanosadchyi.smp.music_app.shared.config;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Refuses to start outside the {@code local} and {@code test} profiles while a secret is missing or still has a value
 * committed to this public repository: the defaults in {@code application-local.yaml} or the values in
 * {@code application-test.yaml}. Registered in {@code META-INF/spring.factories}, so it runs before any bean, and
 * before Flyway, is created. Blank values fail later, in the {@code @Validated} properties.
 */
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SecretsStartupCheck implements EnvironmentPostProcessor {

    static final Map<String, Set<String>> COMMITTED_VALUES = new TreeMap<>(Map.of(
            "jwt.secret", Set.of(
                    "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
                    "7d2f1c9a4e8b3f6a0c5d2e9b8a7f4c1e3b6d9a2f5c8e1b4d7a0f3c6e9b2d5a8f"),
            "encryption.secret-key", Set.of("12345678901234567890123456789012", "test-encryption-key-32-chars-ok!"),
            "minio.secret-key", Set.of("minio_password", "test"),
            "spring.datasource.password", Set.of("postgres")));

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.matchesProfiles("local", "test")) {
            return;
        }
        List<String> missing = new ArrayList<>();
        List<String> committed = new ArrayList<>();
        COMMITTED_VALUES.forEach((property, committedValues) -> {
            String value = valueOf(environment, property);
            if (value == null) {
                missing.add(property);
            } else if (committedValues.contains(value)) {
                committed.add(property);
            }
        });
        if (!missing.isEmpty() || !committed.isEmpty()) {
            throw new IllegalStateException("Secrets are not configured. Missing: " + describe(missing)
                    + "; using a value committed to the repository: " + describe(committed));
        }
    }

    /**
     * Null when the property or the environment variable its placeholder refers to is not set.
     */
    private static String valueOf(ConfigurableEnvironment environment, String property) {
        try {
            return environment.getProperty(property);
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            return null;
        }
    }

    private static String describe(List<String> properties) {
        return properties.isEmpty() ? "none" : String.join(", ", properties);
    }
}
