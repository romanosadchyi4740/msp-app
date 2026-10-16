package com.romanosadchyi.smp.music_app.chats.internal.mapping;

import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.chats.internal.entity.Message;
import com.romanosadchyi.smp.music_app.music.external.TrackLookup;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Maps chat messages to responses with the sender's profile from {@link UserLookup} and the shared track from
 * {@link TrackLookup}; {@link #mapAll} and {@link #mapPage} look up the shared tracks once for all messages.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatMessageMappingService {

    UserLookup userLookup;
    TrackLookup trackLookup;

    public ChatMessageResponseDto map(Message message) {
        return map(message, sharedTracksOf(List.of(message)));
    }

    public List<ChatMessageResponseDto> mapAll(Collection<Message> messages) {
        Map<UUID, TrackResponseDto> tracks = sharedTracksOf(messages);
        return messages.stream()
                .map(message -> map(message, tracks))
                .toList();
    }

    public Page<ChatMessageResponseDto> mapPage(Page<Message> messages) {
        Map<UUID, TrackResponseDto> tracks = sharedTracksOf(messages.getContent());
        return messages.map(message -> map(message, tracks));
    }

    private Map<UUID, TrackResponseDto> sharedTracksOf(Collection<Message> messages) {
        List<UUID> trackIds = messages.stream()
                .map(Message::getSharedTrackId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (trackIds.isEmpty()) {
            return Map.of();
        }
        return trackLookup.findTracks(trackIds).stream()
                .collect(Collectors.toMap(TrackResponseDto::id, Function.identity()));
    }

    private ChatMessageResponseDto map(Message message, Map<UUID, TrackResponseDto> tracks) {
        return ChatMessageResponseDto.builder()
                .id(message.getId())
                .chatId(message.getChat().getId())
                .sender(userLookup.getProfile(message.getSenderId()))
                .content(message.getContent())
                .timestamp(message.getTimestamp())
                .sharedTrack(message.getSharedTrackId() != null ? tracks.get(message.getSharedTrackId()) : null)
                .sharedTrackTimestamp(message.getSharedTrackTimestamp())
                .fileUrl(message.getFileUrl())
                .build();
    }
}
