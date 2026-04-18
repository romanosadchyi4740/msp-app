package com.romanosadchyi.smp.music_app.config;

import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.model.enums.UserRole;
import com.romanosadchyi.smp.music_app.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DataSeeder implements CommandLineRunner {

    UserRepository userRepository;
    public static final UUID TEST_AUTHOR_ID = UUID.fromString("2ee659e2-b01e-4984-8fa2-2890e94b4d24");

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() == 0) {
            log.info("Database is empty. Seeding test data...");

            User testArtist = User.builder()
                    .email("artist@example.com")
                    .displayName("Test Artist")
                    .biography("This is a generated test artist for development.")
                    .avatarUrl("https://ui-avatars.com/api/?name=Test+Artist")
                    .role(UserRole.ROLE_ARTIST)
                    .build();

            testArtist.setId(TEST_AUTHOR_ID);

            userRepository.save(testArtist);

            log.info("Test Artist (User) created with ID: {}", TEST_AUTHOR_ID);
        }
    }

}
