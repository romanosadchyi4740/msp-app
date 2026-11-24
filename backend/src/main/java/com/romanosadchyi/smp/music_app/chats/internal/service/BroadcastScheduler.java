package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMessageRequestDto;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ends broadcasts whose host has gone away (KAN-109) and forgets old ended ones. An ended broadcast is announced in
 * its chat with the same message the host's client posts when it ends one, so the chat stops offering to connect.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class BroadcastScheduler {

    static final String TERMINATED_MESSAGE_PREFIX = "[BROADCAST_NODE_TERMINATED]:";

    BroadcastStateService broadcastStateService;
    ChatMessageService chatMessageService;

    @Scheduled(fixedDelay = 5_000)
    void endAwayHosts() {
        for (BroadcastState ended : broadcastStateService.endAwayHosts()) {
            try {
                chatMessageService.processMessage(ChatMessageRequestDto.builder()
                        .chatId(ended.chatId())
                        .content(TERMINATED_MESSAGE_PREFIX + ended.broadcastId())
                        .build(), ended.hostId());
            } catch (RuntimeException e) {
                // E.g. the host left the chat meanwhile; the listeners were told on the broadcast's topic already
                log.warn("Could not announce the end of broadcast {} in chat {}: {}", ended.broadcastId(),
                        ended.chatId(), e.getClass().getSimpleName());
            }
        }
        broadcastStateService.forgetEnded();
    }
}
