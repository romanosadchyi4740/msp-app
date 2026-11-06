package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.repository.ChatRepository;
import com.romanosadchyi.smp.music_app.shared.security.StompDestinationGuard;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A listening broadcast's STOMP destinations ({@code /topic/broadcast/{chatId}/{broadcastId}} and
 * {@code /app/broadcast/{chatId}/{broadcastId}/sync}) are open only to those who may read the chat it was announced
 * in (KAN-23). Every other {@code /broadcast} destination, the old ones without the chat id among them, is refused.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class BroadcastStompGuard implements StompDestinationGuard {

    static final Pattern BROADCAST_DESTINATION = Pattern.compile("^/(?:topic|app)/broadcast(?:/.*)?$");
    static final Pattern CHAT_BROADCAST = Pattern.compile(
            "^/(?:topic/broadcast/([^/]+)/([^/]+)|app/broadcast/([^/]+)/([^/]+)/sync)$");

    ChatRepository chatRepository;

    @Override
    public boolean guards(String destination) {
        return BROADCAST_DESTINATION.matcher(destination).matches();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean mayAccess(UUID userId, String destination) {
        return chatIdOf(destination)
                .flatMap(chatRepository::findById)
                .map(chat -> ChatMemberships.mayRead(chat, userId))
                .orElse(false);
    }

    /**
     * The chat id of a broadcast destination of the current shape whose ids are both canonical UUIDs.
     */
    static Optional<UUID> chatIdOf(String destination) {
        Matcher matcher = CHAT_BROADCAST.matcher(destination);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String chatId = matcher.group(1) != null ? matcher.group(1) : matcher.group(3);
        String broadcastId = matcher.group(2) != null ? matcher.group(2) : matcher.group(4);
        if (canonicalUuid(broadcastId).isEmpty()) {
            return Optional.empty();
        }
        return canonicalUuid(chatId);
    }

    private static Optional<UUID> canonicalUuid(String value) {
        try {
            UUID uuid = UUID.fromString(value);
            return uuid.toString().equals(value) ? Optional.of(uuid) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
