package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.AlbumNotFoundException;
import com.romanosadchyi.smp.music_app.exception.UserNotFoundException;
import com.romanosadchyi.smp.music_app.exception.TrackNotFoundException;
import com.romanosadchyi.smp.music_app.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.model.dto.request.TrackCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.TrackResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Album;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.model.enums.UserRole;
import com.romanosadchyi.smp.music_app.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.repository.UserRepository;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.service.mapping.TrackMappingService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TrackService {

    TrackRepository trackRepository;
    UserRepository userRepository;
    AlbumRepository albumRepository;
    MinioService minioService;
    TrackMappingService trackMappingService;

    @Transactional
    public void createTrack(UUID authorId, TrackCreateDto dto, MultipartFile file) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + authorId));

        if (author.getRole() != UserRole.ROLE_ARTIST) {
            throw new UserNotAuthorizedException("Only artists can upload tracks");
        }

        Album album = null;
        if (dto.albumId() != null) {
            album = albumRepository.findById(dto.albumId())
                    .orElseThrow(() -> new AlbumNotFoundException("Album not found"));

            if (!album.getAuthor().getId().equals(authorId)) {
                throw new UserNotAuthorizedException("You can only add tracks to your own albums");
            }
        }

        String originalFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalFilename);
        String extWithDot = extension != null ? "." + extension : "";

        Track track = trackMappingService.map(dto).toBuilder()
                .fileKey("PENDING")
                .author(author)
                .album(album)
                .build();

        Track savedTrack = trackRepository.save(track);
        trackRepository.flush();

        String finalFileKey = "tracks/" + savedTrack.getId() + extWithDot;
        minioService.uploadFile(file, finalFileKey);

        savedTrack.setFileKey(finalFileKey);

        if (album != null && savedTrack.getCoverUrl() == null) {
            savedTrack.setCoverUrl(album.getCoverUrl());
        }

        trackRepository.save(savedTrack);
    }

    @Transactional(readOnly = true)
    public String getTrackUrl(UUID trackId) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new TrackNotFoundException("Track not found"));

        return minioService.generatePresignedUrl(track.getFileKey());
    }

    @Transactional(readOnly = true)
    public Page<TrackResponseDto> getAllTracks(int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        return trackRepository.findAll(pageable)
                .map(trackMappingService::map);
    }

}
