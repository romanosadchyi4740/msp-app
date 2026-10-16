package com.romanosadchyi.smp.music_app.config;

import com.romanosadchyi.smp.music_app.model.entity.Genre;
import com.romanosadchyi.smp.music_app.repository.GenreRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Reference data the application needs in every environment (genres).
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReferenceDataSeeder implements CommandLineRunner {

    GenreRepository genreRepository;

    @Override
    @Transactional
    public void run(String @NonNull ... args) {
        if (genreRepository.count() == 0) {
            log.info("Seeding curated list of standard music genres...");
            Map<String, String> genreColors = Map.ofEntries(
                Map.entry("Pop", "#E11D48"),
                Map.entry("Rock", "#DC2626"),
                Map.entry("Hip-Hop", "#EA580C"),
                Map.entry("Electronic", "#10B981"),
                Map.entry("Classical", "#D97706"),
                Map.entry("Jazz", "#0284C7"),
                Map.entry("R&B", "#7C3AED"),
                Map.entry("Metal", "#52525B"),
                Map.entry("Lo-Fi", "#C026D3"),
                Map.entry("Ambient", "#0D9488"),
                Map.entry("J-Rock", "#BE123C"),
                Map.entry("J-Pop", "#F43F5E"),
                Map.entry("Instrumental", "#64748B"),
                Map.entry("Country", "#B45309"),
                Map.entry("Blues", "#2563EB"),
                Map.entry("Folk", "#65A30D"),
                Map.entry("Reggae", "#16A34A"),
                Map.entry("Soul", "#9333EA"),
                Map.entry("Disco", "#DB2777"),
                Map.entry("Funk", "#CA8A04"),
                Map.entry("Punk", "#E11D48"),
                Map.entry("Indie", "#0ea5e9"),
                Map.entry("K-Pop", "#ec4899"),
                Map.entry("Techno", "#14b8a6"),
                Map.entry("House", "#3b82f6")
            );

            for (Map.Entry<String, String> entry : genreColors.entrySet()) {
                String name = entry.getKey();
                String color = entry.getValue();
                Genre genre = Genre.builder()
                    .name(name)
                    .slug(name.toLowerCase().replaceAll("[^a-z0-9]", "-"))
                    .colorHex(color)
                    .build();
                genreRepository.save(genre);
            }
            log.info("Successfully seeded {} genres.", genreColors.size());
        }
    }
}
