package com.romanosadchyi.smp.music_app.music.internal.service;

import com.romanosadchyi.smp.music_app.music.external.TrackNotFoundException;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.music.internal.mapping.TrackMappingService;
import com.romanosadchyi.smp.music_app.music.internal.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.storage.external.FileStorage;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Reading the track catalogue: single tracks, stream URLs, listings, search and an author's tracks.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TrackCatalogService {

    TrackRepository trackRepository;
    UserLookup userLookup;
    FileStorage fileStorage;
    TrackMappingService trackMappingService;

    @Transactional(readOnly = true)
    public TrackResponseDto getTrackById(UUID trackId) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new TrackNotFoundException("Track not found"));
        return trackMappingService.map(track);
    }

    @Transactional(readOnly = true)
    public String getTrackUrl(UUID trackId) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new TrackNotFoundException("Track not found"));

        return fileStorage.presignedGetUrl(track.getFileKey());
    }

    @Transactional(readOnly = true)
    public Page<TrackResponseDto> getAllTracks(int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        return trackMappingService.mapPage(trackRepository.findAll(pageable));
    }

    @Transactional(readOnly = true)
    public Page<TrackResponseDto> searchTracks(String query, UUID authorId, int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        if (authorId != null) {
            return trackMappingService.mapPage(trackRepository.findAllByAuthorIdAndTitleContainingIgnoreCase(authorId, query, pageable));
        }
        return trackMappingService.mapPage(trackRepository.findAllByTitleContainingIgnoreCase(query, pageable));
    }

    @Transactional(readOnly = true)
    public Page<TrackResponseDto> getTracksByGenre(UUID genreId, int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        return trackMappingService.mapPage(trackRepository.findByGenresId(genreId, pageable));
    }

    @Transactional(readOnly = true)
    public List<TrackResponseDto> getTracksByAuthor(UUID authorId) {
        requireUser(authorId);
        return trackMappingService.mapAll(trackRepository.findAllByAuthorId(authorId));
    }

    private void requireUser(UUID userId) {
        if (!userLookup.exists(userId)) {
            throw new UserNotFoundException("User not found with id: " + userId);
        }
    }
}
