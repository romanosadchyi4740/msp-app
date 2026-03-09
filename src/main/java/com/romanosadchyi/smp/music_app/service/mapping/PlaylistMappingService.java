package com.romanosadchyi.smp.music_app.service.mapping;

import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.TrackResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Playlist;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class PlaylistMappingService {

    TrackMappingService trackMappingService;

    public PlaylistResponseDto map(Playlist playlist) {
        List<TrackResponseDto> tracksDto =
                Optional.ofNullable(playlist.getTracks())
                .orElseGet(List::of)
                .stream()
                .map(trackMappingService::map)
                .toList();

        return new PlaylistResponseDto(
                playlist.getId(),
                playlist.getTitle(),
                playlist.getDescription(),
                playlist.getOwnerId(),
                playlist.isPublic(),
                playlist.getCoverUrl(),
                tracksDto,
                tracksDto.size()
        );
    }

}
