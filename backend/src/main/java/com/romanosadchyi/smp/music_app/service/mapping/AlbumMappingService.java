package com.romanosadchyi.smp.music_app.service.mapping;

import com.romanosadchyi.smp.music_app.model.dto.response.AlbumResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.TrackResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Album;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AlbumMappingService {

    TrackMappingService trackMappingService;

    public AlbumResponseDto map(Album album) {
        List<TrackResponseDto> tracksDto =
                Optional.ofNullable(album.getTracks())
                        .orElseGet(List::of)
                        .stream()
                        .map(t -> trackMappingService.map(t, album.getTitle(), album.getCoverUrl()))
                        .toList();

        return new AlbumResponseDto(
                album.getId(),
                album.getTitle(),
                album.getDescription(),
                album.getCoverUrl(),
                album.getAuthor().getId(),
                album.getAuthor().getDisplayName(),
                album.getReleaseDate(),
                tracksDto
        );
    }

}
