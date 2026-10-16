package com.romanosadchyi.smp.music_app.service.mapping;

import com.romanosadchyi.smp.music_app.model.dto.request.TrackCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.GenreResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.TrackResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TrackMappingService {

    GenreMappingService genreMappingService;
    UserLookup userLookup;

    public Track map(TrackCreateDto trackCreateDto) {
        return Track.builder()
                .title(trackCreateDto.title())
                .duration(trackCreateDto.duration())
                .coverUrl(trackCreateDto.coverUrl())
                .trackNumber(trackCreateDto.trackNumber())
                .build();
    }

    public TrackResponseDto map(Track track) {
        String albumTitle = track.getAlbum() != null ? track.getAlbum().getTitle() : null;
        String coverUrl = track.getCoverUrl();
        
        // If track cover is null, check album cover
        if (coverUrl == null && track.getAlbum() != null) {
            coverUrl = track.getAlbum().getCoverUrl();
        }
        
        List<GenreResponseDto> genres = null;
        if (track.getGenres() != null) {
            genres = track.getGenres().stream()
                    .map(genreMappingService::map)
                    .collect(Collectors.toList());
        }
        
        return TrackResponseDto.builder()
                .id(track.getId())
                .title(track.getTitle())
                .authorName(userLookup.getSummary(track.getAuthorId()).displayName())
                .authorId(track.getAuthorId())
                .albumTitle(albumTitle)
                .duration(track.getDuration())
                .coverUrl(coverUrl)
                .genres(genres)
                .build();
    }

    /**
     * Specialized map method for when we already have the album context (avoids LAZY loading issues)
     */
    public TrackResponseDto map(Track track, String albumTitle, String albumCoverUrl) {
        String coverUrl = track.getCoverUrl() != null ? track.getCoverUrl() : albumCoverUrl;
        
        List<GenreResponseDto> genres = null;
        if (track.getGenres() != null) {
            genres = track.getGenres().stream()
                    .map(genreMappingService::map)
                    .collect(Collectors.toList());
        }
        
        return TrackResponseDto.builder()
                .id(track.getId())
                .title(track.getTitle())
                .authorName(userLookup.getSummary(track.getAuthorId()).displayName())
                .authorId(track.getAuthorId())
                .albumTitle(albumTitle)
                .duration(track.getDuration())
                .coverUrl(coverUrl)
                .genres(genres)
                .build();
    }
}
