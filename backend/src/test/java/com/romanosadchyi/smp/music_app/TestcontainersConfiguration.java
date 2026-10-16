package com.romanosadchyi.smp.music_app;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Containers for integration tests, used through {@link IntegrationTest}.
 * <p>
 * The containers are static, so every test context shares one Postgres and one S3 container per test run.
 * {@code destroyMethod = ""} keeps a closing context from stopping them for the others; Testcontainers removes them
 * when the JVM exits. Flyway runs all migrations against the fresh Postgres, so the tests also verify them.
 * <p>
 * S3 is Adobe S3Mock: the {@code minio/minio} image is no longer publicly available, and the MinIO client works
 * against any S3 API.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final int S3_PORT = 9090;

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:15-alpine");

    static final GenericContainer<?> S3 = new GenericContainer<>("adobe/s3mock:4.7.0")
            .withExposedPorts(S3_PORT)
            .waitingFor(Wait.forHttp("/").forPort(S3_PORT));

    @Bean(destroyMethod = "")
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return POSTGRES;
    }

    @Bean(destroyMethod = "")
    GenericContainer<?> s3Container() {
        return S3;
    }

    @Bean
    DynamicPropertyRegistrar s3Properties(GenericContainer<?> s3Container) {
        return registry -> registry.add("minio.url",
                () -> "http://" + s3Container.getHost() + ":" + s3Container.getMappedPort(S3_PORT));
    }

}
