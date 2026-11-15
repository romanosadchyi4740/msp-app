package com.romanosadchyi.smp.music_app.rooms.internal.mapping;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomMessagePage;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomMessageResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomMessage;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserSummary;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Maps room chat messages (KAN-51) to the API models generated from {@code openapi/rooms.yaml}. Authors come from
 * {@link UserLookup}, one call per response.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomChatMappingService {

    UserLookup userLookup;

    public RoomMessageResponse map(RoomMessage message) {
        return toResponse(message, userLookup.findSummary(message.getAuthorId()).orElse(null));
    }

    public RoomMessagePage mapPage(Page<RoomMessage> page) {
        Map<UUID, UserSummary> authors = userLookup.findSummaries(
                page.getContent().stream().map(RoomMessage::getAuthorId).distinct().toList());
        return new RoomMessagePage()
                .content(page.getContent().stream()
                        .map(message -> toResponse(message, authors.get(message.getAuthorId())))
                        .toList())
                .number(page.getNumber())
                .size(page.getSize())
                .numberOfElements(page.getNumberOfElements())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty());
    }

    private static RoomMessageResponse toResponse(RoomMessage message, UserSummary author) {
        return new RoomMessageResponse()
                .id(message.getId())
                .roomId(message.getRoom().getId())
                .authorId(message.getAuthorId())
                .authorName(author != null ? author.displayName() : null)
                .authorAvatarUrl(author != null ? author.avatarUrl() : null)
                .content(message.getContent())
                .sentAt(message.getSentAt());
    }
}
