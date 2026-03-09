package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.AlbumNotFoundException;
import com.romanosadchyi.smp.music_app.exception.AuthorNotFoundException;
import com.romanosadchyi.smp.music_app.model.dto.request.AlbumCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.AlbumResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Album;
import com.romanosadchyi.smp.music_app.model.entity.Author;
import com.romanosadchyi.smp.music_app.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.repository.AuthorRepository;
import com.romanosadchyi.smp.music_app.service.mapping.AlbumMappingService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AlbumService {

    AlbumRepository albumRepository;
    AuthorRepository authorRepository;
    AlbumMappingService albumMappingService;

    @Transactional
    public AlbumResponseDto createAlbum(UUID authorId, AlbumCreateDto dto) {
        Author author = authorRepository.findById(authorId)
                .orElseThrow(() -> new AuthorNotFoundException("Author not found"));

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
    public AlbumResponseDto getAlbumById(UUID albumId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new AlbumNotFoundException("Album not found"));
        return albumMappingService.map(album);
    }

}
