package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.music.external.TrackDeletionCleanup;
import com.romanosadchyi.smp.music_app.repository.MessageRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Detaches a deleted track from chat messages that shared it. It moves into the chats module when that is extracted
 * (KAN-70); playlists drop the track in {@code PlaylistsTrackDeletionCleanup}.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LegacyTrackDeletionCleanup implements TrackDeletionCleanup {

    MessageRepository messageRepository;

    @Override
    public void deleteTrackReferences(UUID trackId) {
        messageRepository.detachTrack(trackId);
    }
}
