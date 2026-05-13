package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.*;
import com.romanosadchyi.smp.music_app.model.dto.request.TrackCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.RecommendedResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.TrackResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Album;
import com.romanosadchyi.smp.music_app.model.entity.Genre;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.model.enums.UserRole;
import com.romanosadchyi.smp.music_app.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.repository.GenreRepository;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.repository.UserRepository;
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

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TrackService {

    TrackRepository trackRepository;
    UserRepository userRepository;
    AlbumRepository albumRepository;
    GenreRepository genreRepository;
    MinioService minioService;
    TrackMappingService trackMappingService;

    @Transactional
    public void createTrack(UUID authorId, TrackCreateDto dto, MultipartFile file) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + authorId));

        if (author.getRole() != UserRole.ROLE_ARTIST) {
            throw new UserNotAuthorizedException("Only artists can upload tracks");
        }

        if (dto.albumId() == null) {
            throw new IllegalArgumentException("Album ID is required. Every track must belong to an album.");
        }
        
        if (dto.genreIds() == null || dto.genreIds().isEmpty() || dto.genreIds().size() > 4) {
            throw new IllegalArgumentException("A track must have at least 1 and no more than 4 genres.");
        }

        Album album = albumRepository.findById(dto.albumId())
                .orElseThrow(() -> new AlbumNotFoundException("Album not found"));

        if (!album.getAuthor().getId().equals(authorId)) {
            throw new UserNotAuthorizedException("You can only add tracks to your own albums");
        }
        
        List<Genre> genres = genreRepository.findAllById(dto.genreIds());
        if (genres.size() != dto.genreIds().size()) {
            throw new IllegalArgumentException("One or more selected genres are invalid.");
        }

        String fileHash = calculateFileHash(file);
        if (trackRepository.existsByFileHash(fileHash)) {
            throw new MusicTrackFileAlreadyExistsException(
                    "This audio file already exists in the system (Duplicate detected)."
            );
        }

        String originalFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalFilename);
        String extWithDot = extension != null ? "." + extension : "";

        Track track = trackMappingService.map(dto).toBuilder()
                .fileKey("PENDING")
                .fileHash(fileHash)
                .author(author)
                .album(album)
                .genres(new HashSet<>(genres))
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

    private String calculateFileHash(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("Failed to calculate file hash", e);
        }
    }

    @Transactional(readOnly = true)
    public String getTrackUrl(UUID trackId) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new TrackNotFoundException("Track not found"));

        return minioService.generatePresignedUrl(track.getFileKey());
    }

    @Transactional(readOnly = true)
    public TrackResponseDto getTrackById(UUID trackId) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new TrackNotFoundException("Track not found"));
        return trackMappingService.map(track);
    }

    @Transactional(readOnly = true)
    public Page<TrackResponseDto> getAllTracks(int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        return trackRepository.findAll(pageable)
                .map(trackMappingService::map);
    }

    @Transactional(readOnly = true)
    public Page<TrackResponseDto> searchTracks(String query, UUID authorId, int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        if (authorId != null) {
            return trackRepository.findAllByAuthorIdAndTitleContainingIgnoreCase(authorId, query, pageable)
                    .map(trackMappingService::map);
        }
        return trackRepository.findAllByTitleContainingIgnoreCase(query, pageable)
                .map(trackMappingService::map);
    }

    @Transactional
    public void likeTrack(UUID userId, UUID trackId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new TrackNotFoundException("Track not found"));

        user.getLikedTracks().add(track);
        userRepository.save(user);
    }

    @Transactional
    public void unlikeTrack(UUID userId, UUID trackId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new TrackNotFoundException("Track not found"));

        user.getLikedTracks().remove(track);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public java.util.List<TrackResponseDto> getLikedTracks(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        return user.getLikedTracks().stream()
                .map(trackMappingService::map)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<TrackResponseDto> getTracksByGenre(UUID genreId, int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        return trackRepository.findByGenresId(genreId, pageable)
                .map(trackMappingService::map);
    }

    @Transactional(readOnly = true)
    public RecommendedResponseDto getRecommendedTracks(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        Set<Track> likedTracks = user.getLikedTracks();
        
        if (likedTracks == null || likedTracks.isEmpty()) {
            Pageable top10 = PageRequest.of(0, 10, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
            List<TrackResponseDto> newestTracks = trackRepository.findAll(top10).stream()
                    .map(trackMappingService::map)
                    .toList();
            return new RecommendedResponseDto("New Arrivals", newestTracks);
        }

        java.util.Map<Genre, Long> genreCounts = likedTracks.stream()
                .flatMap(track -> track.getGenres().stream())
                .collect(Collectors.groupingBy(g -> g, Collectors.counting()));
                
        Genre topGenre = genreCounts.entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey)
                .orElse(null);
                
        if (topGenre == null) {
            Pageable top10 = PageRequest.of(0, 10, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
            List<TrackResponseDto> newestTracks = trackRepository.findAll(top10).stream()
                    .map(trackMappingService::map)
                    .toList();
            return new RecommendedResponseDto("New Arrivals", newestTracks);
        }

        List<UUID> likedTrackIds = likedTracks.stream().map(Track::getId).toList();
        Pageable top10 = PageRequest.of(0, 10, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        
        List<Track> recommendedTracksEntity = trackRepository.findByGenresIdAndIdNotIn(topGenre.getId(), likedTrackIds, top10);
        
        if (recommendedTracksEntity.isEmpty()) {
             recommendedTracksEntity = trackRepository.findByGenresId(topGenre.getId(), top10).getContent();
        }

        List<TrackResponseDto> recommendedTracks = recommendedTracksEntity.stream()
                .map(trackMappingService::map)
                .toList();

        return new RecommendedResponseDto(topGenre.getName(), recommendedTracks);
    }

}
