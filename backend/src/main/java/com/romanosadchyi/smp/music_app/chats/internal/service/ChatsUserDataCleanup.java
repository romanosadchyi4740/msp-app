package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.entity.Chat;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatMembership;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatStatus;
import com.romanosadchyi.smp.music_app.chats.internal.repository.ChatRepository;
import com.romanosadchyi.smp.music_app.users.external.UserDataCleanup;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * On account deletion: the user's messages stay, shown under the anonymized account. The user leaves group chats but
 * stays a member of direct chats, so the other member still sees who they talked to. A direct chat the other member
 * never accepted, and a chat whose remaining members all have deleted accounts, are deleted with their messages.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class ChatsUserDataCleanup implements UserDataCleanup {

    ChatRepository chatRepository;
    UserLookup userLookup;

    @Override
    public void deleteUserData(UUID userId) {
        // Locked first: a member deleting their account at the same time waits and then sees this one as deleted
        List<Chat> chats = chatRepository.findAllByParticipantIdForUpdate(userId);
        Set<UUID> deletedIds = userLookup.findDeletedIds(otherMemberIds(chats, userId));
        for (Chat chat : chats) {
            boolean group = Boolean.TRUE.equals(chat.getIsGroup());
            if (!hasActiveMember(chat, userId, deletedIds) || (!group && !acceptedByOthers(chat, userId))) {
                chatRepository.delete(chat);
            } else if (group) {
                ChatMemberships.find(chat, userId).ifPresent(chat.getMemberships()::remove);
            }
        }
    }

    private static List<UUID> otherMemberIds(List<Chat> chats, UUID userId) {
        return chats.stream()
                .flatMap(chat -> chat.getMemberships().stream())
                .map(ChatMembership::getUserId)
                .filter(memberId -> !memberId.equals(userId))
                .distinct()
                .toList();
    }

    /**
     * Whether the other member of a direct chat accepted it; a pending or rejected invitation from a deleted account is
     * dropped.
     */
    private static boolean acceptedByOthers(Chat chat, UUID userId) {
        return chat.getMemberships().stream()
                .filter(membership -> !membership.getUserId().equals(userId))
                .allMatch(membership -> membership.getStatus() == ChatStatus.ACCEPTED);
    }

    private static boolean hasActiveMember(Chat chat, UUID userId, Set<UUID> deletedIds) {
        return chat.getMemberships().stream()
                .map(ChatMembership::getUserId)
                .anyMatch(memberId -> !memberId.equals(userId) && !deletedIds.contains(memberId));
    }
}
