package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.chats.internal.entity.Chat;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatMembership;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Delivers a chat message to every member's {@code /user/queue/messages}, the sender included, except deleted
 * accounts.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatMessageNotifier {

    static final String MESSAGES_QUEUE = "/queue/messages";

    SimpMessagingTemplate messagingTemplate;
    UserLookup userLookup;

    /**
     * Sends the message to every member except deleted accounts, whose sessions may still be open.
     */
    public void deliver(Chat chat, ChatMessageResponseDto message) {
        List<UUID> memberIds = chat.getMemberships().stream().map(ChatMembership::getUserId).toList();
        Set<UUID> deletedIds = userLookup.findDeletedIds(memberIds);
        memberIds.stream()
                .filter(memberId -> !deletedIds.contains(memberId))
                .forEach(memberId -> messagingTemplate.convertAndSendToUser(memberId.toString(), MESSAGES_QUEUE, message));
    }
}
