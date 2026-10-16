package com.romanosadchyi.smp.music_app.service.mapping;

import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Playlist;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.music.internal.mapping.TrackMappingService;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserSummary;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PlaylistMappingService {

    TrackMappingService trackMappingService;
    UserLookup userLookup;

    public PlaylistResponseDto map(Playlist playlist) {
        List<TrackResponseDto> tracksDto =
                trackMappingService.mapAll(Optional.ofNullable(playlist.getTracks()).orElseGet(List::of));
                
        UUID forkedFromId = null;
        String forkedFromName = null;
        String forkedFromOwnerName = null;
        
        if (playlist.getForkedFrom() != null) {
            forkedFromId = playlist.getForkedFrom().getId();
            forkedFromName = playlist.getForkedFrom().getTitle();
            forkedFromOwnerName = userLookup.findSummary(playlist.getForkedFrom().getOwnerId())
                    .map(UserSummary::displayName)
                    .orElse("Unknown Identity");
        }

        return new PlaylistResponseDto(
                playlist.getId(),
                playlist.getTitle(),
                playlist.getDescription(),
                playlist.getOwnerId(),
                playlist.isPublic(),
                playlist.getCoverUrl(),
                tracksDto,
                tracksDto.size(),
                forkedFromId,
                forkedFromName,
                forkedFromOwnerName
        );
    }
}
