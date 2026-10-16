package com.romanosadchyi.smp.music_app;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class MusicAppApplicationTests {

	@Autowired
	DataSource dataSource;

	@Autowired
	Flyway flyway;

	@Test
	void contextLoads() {
	}

	@Test
	void usesTestcontainersDatabase() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.getMetaData().getURL()).isEqualTo(TestcontainersConfiguration.POSTGRES.getJdbcUrl());
		}
	}

	@Test
	void allMigrationsApplied() {
		assertThat(flyway.info().pending()).isEmpty();
		assertThat(flyway.info().applied())
				.isNotEmpty()
				.allSatisfy(migration -> assertThat(migration.getState().isFailed()).isFalse());
	}

}
