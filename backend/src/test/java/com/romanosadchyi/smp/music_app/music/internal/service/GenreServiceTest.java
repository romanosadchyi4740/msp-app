package com.romanosadchyi.smp.music_app.music.internal.service;

import com.romanosadchyi.smp.music_app.music.external.GenreResponseDto;
import com.romanosadchyi.smp.music_app.music.internal.entity.Genre;
import com.romanosadchyi.smp.music_app.music.internal.mapping.GenreMappingService;
import com.romanosadchyi.smp.music_app.music.internal.repository.GenreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenreServiceTest {

    @Mock
    GenreRepository genreRepository;

    @Mock
    GenreMappingService genreMappingService;

    @InjectMocks
    GenreService genreService;

    @Test
    void getAllGenres_ReturnsList() {
        Genre genre = Genre.builder().id(UUID.randomUUID()).name("Pop").build();
        when(genreRepository.findAll(ArgumentMatchers.any(Sort.class))).thenReturn(List.of(genre));
        when(genreMappingService.map(genre)).thenReturn(new GenreResponseDto(genre.getId(), "Pop", null, null));

        List<GenreResponseDto> result = genreService.getAllGenres();

        assertEquals(1, result.size());
        assertEquals("Pop", result.get(0).name());
    }
}
