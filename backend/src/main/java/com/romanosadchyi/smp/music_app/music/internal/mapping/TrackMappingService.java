package com.romanosadchyi.smp.music_app.music.internal.mapping;

import com.romanosadchyi.smp.music_app.music.external.GenreResponseDto;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.music.internal.dto.TrackCreateDto;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserSummary;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maps tracks to responses. Author names come from {@link UserLookup}; the collection and page variants look up all
 * authors in one call instead of one per track.
 */
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
        return map(track, userLookup.getSummary(track.getAuthorId()).displayName());
    }

    public List<TrackResponseDto> mapAll(Collection<Track> tracks) {
        Map<UUID, UserSummary> authors = authorsOf(tracks);
        return tracks.stream()
                .map(track -> map(track, authorName(authors, track)))
                .toList();
    }

    public Page<TrackResponseDto> mapPage(Page<Track> tracks) {
        Map<UUID, UserSummary> authors = authorsOf(tracks.getContent());
        return tracks.map(track -> map(track, authorName(authors, track)));
    }

    /**
     * For a track listed inside its album: the album's title and cover are given, so the lazy album is not loaded.
     */
    public TrackResponseDto map(Track track, String albumTitle, String albumCoverUrl, Map<UUID, UserSummary> authors) {
        return toDto(track, albumTitle, track.getCoverUrl() != null ? track.getCoverUrl() : albumCoverUrl,
                authorName(authors, track));
    }

    /**
     * The authors of the given tracks, by id.
     */
    public Map<UUID, UserSummary> authorsOf(Collection<Track> tracks) {
        return userLookup.findSummaries(tracks.stream().map(Track::getAuthorId).distinct().toList());
    }

    private TrackResponseDto map(Track track, String authorName) {
        String albumTitle = track.getAlbum() != null ? track.getAlbum().getTitle() : null;
        String coverUrl = track.getCoverUrl();
        if (coverUrl == null && track.getAlbum() != null) {
            coverUrl = track.getAlbum().getCoverUrl();
        }
        return toDto(track, albumTitle, coverUrl, authorName);
    }

    private TrackResponseDto toDto(Track track, String albumTitle, String coverUrl, String authorName) {
        List<GenreResponseDto> genres = track.getGenres() != null
                ? track.getGenres().stream().map(genreMappingService::map).toList()
                : null;

        return TrackResponseDto.builder()
                .id(track.getId())
                .title(track.getTitle())
                .authorName(authorName)
                .authorId(track.getAuthorId())
                .albumTitle(albumTitle)
                .duration(track.getDuration())
                .coverUrl(coverUrl)
                .genres(genres)
                .build();
    }

    private static String authorName(Map<UUID, UserSummary> authors, Track track) {
        UserSummary author = authors.get(track.getAuthorId());
        return author != null ? author.displayName() : null;
    }
}
