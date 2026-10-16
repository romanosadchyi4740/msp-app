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
import com.romanosadchyi.smp.music_app.users.external.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackUploadValidatorTest {

    @Mock
    UserLookup userLookup;

    @Mock
    AlbumRepository albumRepository;

    @Mock
    GenreRepository genreRepository;

    @InjectMocks
    TrackUploadValidator validator;

    final UUID authorId = UUID.randomUUID();
    final UUID albumId = UUID.randomUUID();
    final Genre genre = Genre.builder().id(UUID.randomUUID()).build();

    @BeforeEach
    void artist() {
        when(userLookup.getSummary(authorId)).thenReturn(new UserSummary(authorId, "Artist", null, UserRole.ROLE_ARTIST));
    }

    @Test
    void validUpload_ReturnsAlbumAndGenres() {
        Album album = Album.builder().id(albumId).authorId(authorId).build();
        when(albumRepository.findById(albumId)).thenReturn(Optional.of(album));
        when(genreRepository.findAllById(Set.of(genre.getId()))).thenReturn(List.of(genre));

        TrackUploadValidator.Target target = validator.validate(authorId, dto(albumId, Set.of(genre.getId())));

        assertThat(target.album()).isEqualTo(album);
        assertThat(target.genres()).containsExactly(genre);
    }

    @Test
    void missingAlbumId_IsRejected() {
        assertThatThrownBy(() -> validator.validate(authorId, dto(null, Set.of(genre.getId()))))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(albumRepository);
    }

    @Test
    void noGenresOrMoreThanFour_IsRejected() {
        Set<UUID> fiveGenres = Set.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> validator.validate(authorId, dto(albumId, Set.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(authorId, dto(albumId, fiveGenres)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownAlbum_IsRejected() {
        when(albumRepository.findById(albumId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate(authorId, dto(albumId, Set.of(genre.getId()))))
                .isInstanceOf(AlbumNotFoundException.class);
    }

    @Test
    void someoneElsesAlbum_IsRejected() {
        when(albumRepository.findById(albumId)).thenReturn(Optional.of(Album.builder().id(albumId).authorId(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> validator.validate(authorId, dto(albumId, Set.of(genre.getId()))))
                .isInstanceOf(UserNotAuthorizedException.class);
        verifyNoInteractions(genreRepository);
    }

    @Test
    void unknownGenre_IsRejected() {
        when(albumRepository.findById(albumId)).thenReturn(Optional.of(Album.builder().id(albumId).authorId(authorId).build()));
        when(genreRepository.findAllById(Set.of(genre.getId()))).thenReturn(List.of());

        assertThatThrownBy(() -> validator.validate(authorId, dto(albumId, Set.of(genre.getId()))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static TrackCreateDto dto(UUID albumId, Set<UUID> genreIds) {
        return new TrackCreateDto("Track", 120, null, albumId, null, genreIds);
    }
}
