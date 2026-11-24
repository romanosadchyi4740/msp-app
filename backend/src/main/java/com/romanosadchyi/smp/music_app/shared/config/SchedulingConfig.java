package com.romanosadchyi.smp.music_app.shared.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * The clock services read the time from, so tests can fix it (sync math is in UTC), and scheduled jobs. The test
 * profile turns the jobs off with {@code scheduling.enabled=false}, so they never race the integration tests; tests
 * call the jobs' services directly.
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class Jobs {
    }
}
