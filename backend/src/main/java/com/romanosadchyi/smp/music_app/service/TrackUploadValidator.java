package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.AlbumNotFoundException;
import com.romanosadchyi.smp.music_app.model.dto.request.TrackCreateDto;
import com.romanosadchyi.smp.music_app.model.entity.Album;
import com.romanosadchyi.smp.music_app.model.entity.Genre;
import com.romanosadchyi.smp.music_app.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.repository.GenreRepository;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserRole;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Checks a track upload before anything is stored: the author is an artist, the album exists and is theirs, and
 * the track has 1 to 4 existing genres.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TrackUploadValidator {

    static final int MAX_GENRES = 4;

    UserLookup userLookup;
    AlbumRepository albumRepository;
    GenreRepository genreRepository;

    /**
     * The album and genres the new track belongs to.
     */
    public record Target(Album album, List<Genre> genres) {
    }

    /**
     * @throws UserNotAuthorizedException if the author is not an artist or does not own the album
     * @throws AlbumNotFoundException     if the album does not exist
     * @throws IllegalArgumentException   if the album id is missing or the genres are invalid
     */
    public Target validate(UUID authorId, TrackCreateDto dto) {
        if (userLookup.getSummary(authorId).role() != UserRole.ROLE_ARTIST) {
            throw new UserNotAuthorizedException("Only artists can upload tracks");
        }

        if (dto.albumId() == null) {
            throw new IllegalArgumentException("Album ID is required. Every track must belong to an album.");
        }

        if (dto.genreIds() == null || dto.genreIds().isEmpty() || dto.genreIds().size() > MAX_GENRES) {
            throw new IllegalArgumentException("A track must have at least 1 and no more than 4 genres.");
        }

        Album album = albumRepository.findById(dto.albumId())
                .orElseThrow(() -> new AlbumNotFoundException("Album not found"));

        if (!album.getAuthorId().equals(authorId)) {
            throw new UserNotAuthorizedException("You can only add tracks to your own albums");
        }

        List<Genre> genres = genreRepository.findAllById(dto.genreIds());
        if (genres.size() != dto.genreIds().size()) {
            throw new IllegalArgumentException("One or more selected genres are invalid.");
        }

        return new Target(album, genres);
    }
}
