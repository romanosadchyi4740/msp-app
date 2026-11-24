package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.dto.BroadcastStateDto;
import com.romanosadchyi.smp.music_app.chats.internal.mapping.BroadcastStateMappingService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Sends broadcast states (KAN-109): every change to the broadcast's topic, a snapshot only to the member who asked.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class BroadcastNotifier {

    SimpMessagingTemplate messagingTemplate;
    BroadcastStateMappingService mappingService;

    static String topic(UUID chatId, UUID broadcastId) {
        return "/topic/broadcast/" + chatId + "/" + broadcastId;
    }

    static String snapshotQueue(UUID chatId, UUID broadcastId) {
        return "/queue/broadcast/" + chatId + "/" + broadcastId + "/snapshot";
    }

    void publish(BroadcastState state) {
        messagingTemplate.convertAndSend(topic(state.chatId(), state.broadcastId()), mappingService.map(state));
    }

    void sendSnapshot(UUID userId, UUID chatId, UUID broadcastId, BroadcastState state) {
        BroadcastStateDto snapshot = state == null
                ? mappingService.unknown(broadcastId, chatId)
                : mappingService.map(state);
        messagingTemplate.convertAndSendToUser(userId.toString(), snapshotQueue(chatId, broadcastId), snapshot);
    }
}
