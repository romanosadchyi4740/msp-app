package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.repository.MessageRepository;
import com.romanosadchyi.smp.music_app.music.external.TrackDeletionCleanup;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Detaches a deleted track from the chat messages that shared it; the messages stay.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class ChatsTrackDeletionCleanup implements TrackDeletionCleanup {

    MessageRepository messageRepository;

    @Override
    public void deleteTrackReferences(UUID trackId) {
        messageRepository.detachTrack(trackId);
    }
}
