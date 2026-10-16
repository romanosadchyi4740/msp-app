package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.dto.response.GenreResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Genre;
import com.romanosadchyi.smp.music_app.repository.GenreRepository;
import com.romanosadchyi.smp.music_app.service.mapping.GenreMappingService;
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
