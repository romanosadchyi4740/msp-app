package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.AlbumNotFoundException;
import com.romanosadchyi.smp.music_app.exception.UserNotFoundException;
import com.romanosadchyi.smp.music_app.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.model.dto.request.AlbumCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.AlbumResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Album;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.model.enums.UserRole;
import com.romanosadchyi.smp.music_app.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.repository.UserRepository;
import com.romanosadchyi.smp.music_app.service.mapping.AlbumMappingService;
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
    UserRepository userRepository;
    AlbumMappingService albumMappingService;

    @Transactional
    public AlbumResponseDto createAlbum(UUID authorId, AlbumCreateDto dto) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (author.getRole() != UserRole.ROLE_ARTIST) {
            throw new UserNotAuthorizedException("Only artists can create albums");
        }

        Album album = Album.builder()
                .title(dto.title())
                .description(dto.description())
                .coverUrl(dto.coverUrl())
                .releaseDate(LocalDate.now())
                .author(author)
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
    public Page<AlbumResponseDto> searchAlbums(String query, Pageable pageable) {
        return albumRepository.findAllByTitleContainingIgnoreCase(query, pageable)
                .map(albumMappingService::map);
    }

}
