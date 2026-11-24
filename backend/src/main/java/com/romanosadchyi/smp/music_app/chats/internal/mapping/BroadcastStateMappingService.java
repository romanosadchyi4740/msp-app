package com.romanosadchyi.smp.music_app.chats.internal.mapping;

import com.romanosadchyi.smp.music_app.chats.internal.dto.BroadcastStateDto;
import com.romanosadchyi.smp.music_app.chats.internal.dto.BroadcastStatus;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/**
 * Builds the {@link BroadcastStateDto} clients receive, stamped with the server time of sending.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BroadcastStateMappingService {

    Clock clock;

    public BroadcastStateDto map(UUID broadcastId, UUID chatId, UUID hostId, BroadcastStatus status, UUID trackId,
                                 double position, boolean playing, long anchorTime, long version) {
        return BroadcastStateDto.builder()
                .broadcastId(broadcastId)
                .chatId(chatId)
                .hostId(hostId)
                .status(status)
                .trackId(trackId)
                .position(position)
                .playing(playing)
                .anchorTime(anchorTime)
                .serverTime(clock.millis())
                .version(version)
                .build();
    }

    /**
     * The answer for a broadcast the server does not know (never started, or forgotten after a restart): ended, so a
     * listener stops waiting for it.
     */
    public BroadcastStateDto unknown(UUID broadcastId, UUID chatId) {
        long now = clock.millis();
        return BroadcastStateDto.builder()
                .broadcastId(broadcastId)
                .chatId(chatId)
                .status(BroadcastStatus.TERMINATED)
                .anchorTime(now)
                .serverTime(now)
                .build();
    }
}
