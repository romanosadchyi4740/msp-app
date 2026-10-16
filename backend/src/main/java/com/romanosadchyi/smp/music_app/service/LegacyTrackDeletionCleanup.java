package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.music.external.TrackDeletionCleanup;
import com.romanosadchyi.smp.music_app.repository.MessageRepository;
import com.romanosadchyi.smp.music_app.repository.PlaylistRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Removes a deleted track from playlists and from chat messages that shared it. It moves into the playlists and
 * chats modules, split by the data each owns, when they are extracted (KAN-69, KAN-70).
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LegacyTrackDeletionCleanup implements TrackDeletionCleanup {

    PlaylistRepository playlistRepository;
    MessageRepository messageRepository;

    @Override
    public void deleteTrackReferences(UUID trackId) {
        // Through the entity, so Hibernate renumbers track_order instead of leaving a gap
        playlistRepository.findAllContainingTrack(trackId)
                .forEach(playlist -> playlist.getTracks().removeIf(track -> track.getId().equals(trackId)));
        messageRepository.detachTrack(trackId);
    }
}
