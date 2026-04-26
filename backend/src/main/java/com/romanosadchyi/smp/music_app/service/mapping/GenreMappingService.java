package com.romanosadchyi.smp.music_app.service.mapping;

import com.romanosadchyi.smp.music_app.model.dto.response.GenreResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Genre;
import org.springframework.stereotype.Service;

@Service
public class GenreMappingService {

    public GenreResponseDto map(Genre genre) {
        return GenreResponseDto.builder()
                .id(genre.getId())
                .name(genre.getName())
                .slug(genre.getSlug())
                .build();
    }
}