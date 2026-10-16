package com.romanosadchyi.smp.music_app;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Full application context on Testcontainers (Postgres + S3) with the {@code test} profile, never the local
 * docker-compose services. Needs only a Docker daemon. Every integration test uses exactly this annotation, so
 * they all share one cached context. Excluded from the unit-test run with {@code -DexcludedGroups=integration}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Tag("integration")
public @interface IntegrationTest {
}
