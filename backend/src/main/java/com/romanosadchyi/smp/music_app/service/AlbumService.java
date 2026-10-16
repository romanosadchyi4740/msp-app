package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.AlbumNotFoundException;
import com.romanosadchyi.smp.music_app.model.dto.request.AlbumCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.AlbumResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Album;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.repository.MessageRepository;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.service.mapping.AlbumMappingService;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AlbumService {

    AlbumRepository albumRepository;
    UserLookup userLookup;
    TrackRepository trackRepository;
    MessageRepository messageRepository;
    MinioService minioService;
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
        return albumRepository.findAll(pageable)
                .map(albumMappingService::map);
    }

    @Transactional(readOnly = true)
    public List<AlbumResponseDto> getAlbumsByAuthor(UUID authorId) {
        return albumRepository.findAllByAuthorId(authorId).stream()
                .map(albumMappingService::map)
                .collect(Collectors.toList());
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
            return albumRepository.findAllByAuthorIdAndTitleContainingIgnoreCase(authorId, query, pageable)
                    .map(albumMappingService::map);
        }
        return albumRepository.findAllByTitleContainingIgnoreCase(query, pageable)
                .map(albumMappingService::map);
    }

    @Transactional
    public void deleteAlbum(UUID albumId, UUID authorId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new AlbumNotFoundException("Album not found"));

        if (!album.getAuthorId().equals(authorId)) {
            throw new UserNotAuthorizedException("Access Denied: You do not own this collection instance.");
        }

        log.info("Purging Collection: {} (ID: {})", album.getTitle(), albumId);

        album.getTracks().forEach(track -> {
            // 1. Physical Cleanup
            minioService.deleteFile(track.getFileKey());
            
            // 2. Message Decoupling
            messageRepository.detachTrack(track);

            // 3. Collections Cleanup
            trackRepository.deleteFromPlaylistTracks(track.getId());
            trackRepository.deleteFromTrackGenres(track.getId());
        });

        // 4. Cascade delete Album + Tracks
        albumRepository.delete(album);
    }
}
