package com.romanosadchyi.smp.music_app.music.internal.mapping;

import com.romanosadchyi.smp.music_app.music.external.GenreResponseDto;
import com.romanosadchyi.smp.music_app.music.internal.entity.Genre;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GenreMappingServiceTest {

    final GenreMappingService genreMappingService = new GenreMappingService();

    @Test
    void map_ReturnsDto() {
        UUID id = UUID.randomUUID();
        Genre genre = Genre.builder()
                .id(id)
                .name("Rock")
                .slug("rock")
                .colorHex("#ffffff")
                .build();

        GenreResponseDto result = genreMappingService.map(genre);

        assertNotNull(result);
        assertEquals(id, result.id());
        assertEquals("Rock", result.name());
        assertEquals("rock", result.slug());
        assertEquals("#ffffff", result.colorHex());
    }
}
