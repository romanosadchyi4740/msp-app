package com.romanosadchyi.smp.music_app.service.mapping;

import com.romanosadchyi.smp.music_app.model.dto.request.TrackCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.TrackResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import org.springframework.stereotype.Component;

@Component
public class TrackMappingService {

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
        
        return TrackResponseDto.builder()
                .id(track.getId())
                .title(track.getTitle())
                .authorName(track.getAuthor().getDisplayName())
                .authorId(track.getAuthor().getId())
                .albumTitle(albumTitle)
                .duration(track.getDuration())
                .coverUrl(coverUrl)
                .build();
    }

    /**
     * Specialized map method for when we already have the album context (avoids LAZY loading issues)
     */
    public TrackResponseDto map(Track track, String albumTitle, String albumCoverUrl) {
        String coverUrl = track.getCoverUrl() != null ? track.getCoverUrl() : albumCoverUrl;
        
        return TrackResponseDto.builder()
                .id(track.getId())
                .title(track.getTitle())
                .authorName(track.getAuthor().getDisplayName())
                .authorId(track.getAuthor().getId())
                .albumTitle(albumTitle)
                .duration(track.getDuration())
                .coverUrl(coverUrl)
                .build();
    }

}
