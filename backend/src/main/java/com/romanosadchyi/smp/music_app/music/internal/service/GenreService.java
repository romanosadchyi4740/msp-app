package com.romanosadchyi.smp.music_app.music.internal.service;

import com.romanosadchyi.smp.music_app.music.external.GenreResponseDto;
import com.romanosadchyi.smp.music_app.music.internal.mapping.GenreMappingService;
import com.romanosadchyi.smp.music_app.music.internal.repository.GenreRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GenreService {

    GenreRepository genreRepository;
    GenreMappingService genreMappingService;

    public List<GenreResponseDto> getAllGenres() {
        return genreRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                .map(genreMappingService::map)
                .collect(Collectors.toList());
    }
}
