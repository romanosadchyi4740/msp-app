package com.romanosadchyi.smp.music_app.config;

import com.romanosadchyi.smp.music_app.model.entity.Genre;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.model.enums.UserRole;
import com.romanosadchyi.smp.music_app.repository.GenreRepository;
import com.romanosadchyi.smp.music_app.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(name = "data-seeder.enabled", havingValue = "true")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DataSeeder implements CommandLineRunner {

    UserRepository userRepository;
    GenreRepository genreRepository;
    
    public static final UUID TEST_AUTHOR_ID = UUID.fromString("2ee659e2-b01e-4984-8fa2-2890e94b4d24");

    @Override
    @Transactional
    public void run(String @NonNull ... args) {
        if (userRepository.count() == 0) {
            log.info("Database is empty. Seeding test user...");

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

        if (genreRepository.count() == 0) {
            log.info("Seeding curated list of standard music genres...");
            List<String> genreNames = List.of(
                "Pop", "Rock", "Hip-Hop", "Electronic", "Classical", 
                "Jazz", "R&B", "Metal", "Lo-Fi", "Ambient", 
                "J-Rock", "J-Pop", "Instrumental", "Country", "Blues",
                "Folk", "Reggae", "Soul", "Disco", "Funk",
                "Punk", "Indie", "K-Pop", "Techno", "House"
            );

            for (String name : genreNames) {
                Genre genre = Genre.builder()
                    .name(name)
                    .slug(name.toLowerCase().replaceAll("[^a-z0-9]", "-"))
                    .build();
                genreRepository.save(genre);
            }
            log.info("Successfully seeded {} genres.", genreNames.size());
        }
    }

}
