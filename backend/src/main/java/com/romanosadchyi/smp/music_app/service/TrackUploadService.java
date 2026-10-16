package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.MusicTrackFileAlreadyExistsException;
import com.romanosadchyi.smp.music_app.model.dto.request.TrackCreateDto;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.service.mapping.TrackMappingService;
import com.romanosadchyi.smp.music_app.storage.external.FileStorage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Uploading a track: validates it, rejects audio files already in the system, saves the track and stores the file
 * under {@code tracks/<track id>.<extension>}.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TrackUploadService {

    TrackUploadValidator trackUploadValidator;
    TrackRepository trackRepository;
    FileStorage fileStorage;
    TrackMappingService trackMappingService;

    @Transactional
    public void createTrack(UUID authorId, TrackCreateDto dto, MultipartFile file) {
        TrackUploadValidator.Target target = trackUploadValidator.validate(authorId, dto);
        String fileHash = sha256Hex(file);
        ensureNotDuplicate(fileHash);

        // The file key contains the generated track id, so the track is saved first
        Track savedTrack = trackRepository.save(buildTrack(authorId, dto, target, fileHash));
        trackRepository.flush();

        savedTrack.setFileKey(storeFile(savedTrack.getId(), file));
        if (savedTrack.getCoverUrl() == null) {
            savedTrack.setCoverUrl(target.album().getCoverUrl());
        }

        trackRepository.save(savedTrack);
    }

    private void ensureNotDuplicate(String fileHash) {
        if (trackRepository.existsByFileHash(fileHash)) {
            throw new MusicTrackFileAlreadyExistsException(
                    "This audio file already exists in the system (Duplicate detected)."
            );
        }
    }

    private Track buildTrack(UUID authorId, TrackCreateDto dto, TrackUploadValidator.Target target, String fileHash) {
        return trackMappingService.map(dto).toBuilder()
                .fileKey("PENDING")
                .fileHash(fileHash)
                .authorId(authorId)
                .album(target.album())
                .genres(new HashSet<>(target.genres()))
                .build();
    }

    private String storeFile(UUID trackId, MultipartFile file) {
        String fileKey = "tracks/" + trackId + fileExtension(file);
        fileStorage.upload(file, fileKey);
        return fileKey;
    }

    private static String fileExtension(MultipartFile file) {
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        return extension != null ? "." + extension : "";
    }

    private static String sha256Hex(MultipartFile file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes()));
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("Failed to calculate file hash", e);
        }
    }
}
