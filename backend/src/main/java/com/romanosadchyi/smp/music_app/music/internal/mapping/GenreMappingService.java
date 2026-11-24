package com.romanosadchyi.smp.music_app.music.internal.mapping;

import com.romanosadchyi.smp.music_app.music.external.GenreResponseDto;
import com.romanosadchyi.smp.music_app.music.internal.entity.Genre;
import org.springframework.stereotype.Service;

@Service
public class GenreMappingService {

    public GenreResponseDto map(Genre genre) {
        return GenreResponseDto.builder()
                .id(genre.getId())
                .name(genre.getName())
                .slug(genre.getSlug())
                .colorHex(genre.getColorHex())
                .build();
    }
}
