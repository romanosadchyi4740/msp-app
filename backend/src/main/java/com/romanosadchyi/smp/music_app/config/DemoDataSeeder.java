package com.romanosadchyi.smp.music_app.config;

import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.model.enums.UserRole;
import com.romanosadchyi.smp.music_app.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo data for local development only. Never active in {@code prod}; can be switched off locally with
 * {@code DATA_SEEDER_ENABLED=false}.
 */
@Slf4j
@Component
@Profile("local")
@ConditionalOnProperty(name = "data-seeder.enabled", havingValue = "true")
@Order(2)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DemoDataSeeder implements CommandLineRunner {

    public static final String DEMO_ARTIST_EMAIL = "artist@example.com";

    UserRepository userRepository;

    @Override
    @Transactional
    public void run(String @NonNull ... args) {
        if (userRepository.count() == 0) {
            log.info("Database is empty. Seeding test user...");

            User testArtist = User.builder()
                    .email(DEMO_ARTIST_EMAIL)
                    .displayName("Test Artist")
                    .biography("This is a generated test artist for development.")
                    .avatarUrl("https://ui-avatars.com/api/?name=Test+Artist")
                    .role(UserRole.ROLE_ARTIST)
                    .build();

            // The id is generated: assigning one makes save() merge a row that does not exist, which fails
            testArtist = userRepository.save(testArtist);
            log.info("Test Artist (User) created with ID: {}", testArtist.getId());
        }
    }

}
