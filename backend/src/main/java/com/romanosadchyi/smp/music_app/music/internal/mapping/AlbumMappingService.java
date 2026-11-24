package com.romanosadchyi.smp.music_app.music.internal.mapping;

import com.romanosadchyi.smp.music_app.music.external.AlbumResponseDto;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.music.internal.entity.Album;
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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Maps albums with their tracks to responses, looking up all album and track authors in one call.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AlbumMappingService {

    TrackMappingService trackMappingService;
    UserLookup userLookup;

    public AlbumResponseDto map(Album album) {
        return map(album, authorsOf(List.of(album)));
    }

    public List<AlbumResponseDto> mapAll(Collection<Album> albums) {
        Map<UUID, UserSummary> authors = authorsOf(albums);
        return albums.stream()
                .map(album -> map(album, authors))
                .toList();
    }

    public Page<AlbumResponseDto> mapPage(Page<Album> albums) {
        Map<UUID, UserSummary> authors = authorsOf(albums.getContent());
        return albums.map(album -> map(album, authors));
    }

    private AlbumResponseDto map(Album album, Map<UUID, UserSummary> authors) {
        List<TrackResponseDto> tracksDto = tracksOf(album).stream()
                .map(track -> trackMappingService.map(track, album.getTitle(), album.getCoverUrl(), authors))
                .toList();

        UserSummary author = authors.get(album.getAuthorId());
        return new AlbumResponseDto(
                album.getId(),
                album.getTitle(),
                album.getDescription(),
                album.getCoverUrl(),
                album.getAuthorId(),
                author != null ? author.displayName() : null,
                album.getReleaseDate(),
                tracksDto
        );
    }

    private Map<UUID, UserSummary> authorsOf(Collection<Album> albums) {
        List<UUID> authorIds = albums.stream()
                .flatMap(album -> Stream.concat(
                        Stream.of(album.getAuthorId()),
                        tracksOf(album).stream().map(Track::getAuthorId)))
                .distinct()
                .toList();
        return userLookup.findSummaries(authorIds);
    }

    private static List<Track> tracksOf(Album album) {
        return Optional.ofNullable(album.getTracks()).orElseGet(List::of);
    }
}
