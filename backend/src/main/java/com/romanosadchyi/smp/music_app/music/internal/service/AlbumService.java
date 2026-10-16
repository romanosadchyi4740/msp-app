package com.romanosadchyi.smp.music_app.music.internal.service;

import com.romanosadchyi.smp.music_app.music.external.AlbumResponseDto;
import com.romanosadchyi.smp.music_app.music.internal.dto.AlbumCreateDto;
import com.romanosadchyi.smp.music_app.music.internal.entity.Album;
import com.romanosadchyi.smp.music_app.music.internal.exception.AlbumNotFoundException;
import com.romanosadchyi.smp.music_app.music.internal.mapping.AlbumMappingService;
import com.romanosadchyi.smp.music_app.music.internal.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserRole;
import com.romanosadchyi.smp.music_app.users.external.UserSummary;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AlbumService {

    AlbumRepository albumRepository;
    UserLookup userLookup;
    TrackRemover trackRemover;
    AlbumMappingService albumMappingService;

    @Transactional
    public AlbumResponseDto createAlbum(UUID authorId, AlbumCreateDto dto) {
        UserSummary author = userLookup.getSummary(authorId);

        if (author.role() != UserRole.ROLE_ARTIST) {
            throw new UserNotAuthorizedException("Only artists can create albums");
        }

        Album album = Album.builder()
                .title(dto.title())
                .description(dto.description())
                .coverUrl(dto.coverUrl())
                .releaseDate(LocalDate.now())
                .authorId(authorId)
                .build();

        Album savedAlbum = albumRepository.save(album);
        return albumMappingService.map(savedAlbum);
    }

    @Transactional(readOnly = true)
    public Page<AlbumResponseDto> getAllAlbums(Pageable pageable) {
        return albumMappingService.mapPage(albumRepository.findAll(pageable));
    }

    @Transactional(readOnly = true)
    public List<AlbumResponseDto> getAlbumsByAuthor(UUID authorId) {
        return albumMappingService.mapAll(albumRepository.findAllByAuthorId(authorId));
    }

    @Transactional(readOnly = true)
    public AlbumResponseDto getAlbumById(UUID albumId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new AlbumNotFoundException("Album not found"));
        return albumMappingService.map(album);
    }

    @Transactional(readOnly = true)
    public Page<AlbumResponseDto> searchAlbums(String query, UUID authorId, Pageable pageable) {
        if (authorId != null) {
            return albumMappingService.mapPage(albumRepository.findAllByAuthorIdAndTitleContainingIgnoreCase(authorId, query, pageable));
        }
        return albumMappingService.mapPage(albumRepository.findAllByTitleContainingIgnoreCase(query, pageable));
    }

    @Transactional
    public void deleteAlbum(UUID albumId, UUID authorId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new AlbumNotFoundException("Album not found"));

        if (!album.getAuthorId().equals(authorId)) {
            throw new UserNotAuthorizedException("Access Denied: You do not own this collection instance.");
        }

        log.info("Purging Collection: {} (ID: {})", album.getTitle(), albumId);

        album.getTracks().forEach(trackRemover::releaseTrack);

        // Deletes the tracks too (cascade)
        albumRepository.delete(album);
    }
}
