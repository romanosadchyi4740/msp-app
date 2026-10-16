package com.romanosadchyi.smp.music_app.config;

import com.romanosadchyi.smp.music_app.repository.GenreRepository;
import com.romanosadchyi.smp.music_app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DataSeederProfilesTest {

    final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withBean(GenreRepository.class, () -> mock(GenreRepository.class))
            .withUserConfiguration(ReferenceDataSeeder.class, DemoDataSeeder.class);

    @Test
    void localProfile_SeedsReferenceAndDemoData() {
        contextRunner
                .withPropertyValues("spring.profiles.active=local", "data-seeder.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ReferenceDataSeeder.class);
                    assertThat(context).hasSingleBean(DemoDataSeeder.class);
                });
    }

    @Test
    void localProfile_DemoDataDisabled_SeedsReferenceDataOnly() {
        contextRunner
                .withPropertyValues("spring.profiles.active=local", "data-seeder.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(ReferenceDataSeeder.class);
                    assertThat(context).doesNotHaveBean(DemoDataSeeder.class);
                });
    }

    @Test
    void prodProfile_NeverSeedsDemoData() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod", "data-seeder.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ReferenceDataSeeder.class);
                    assertThat(context).doesNotHaveBean(DemoDataSeeder.class);
                });
    }
}
