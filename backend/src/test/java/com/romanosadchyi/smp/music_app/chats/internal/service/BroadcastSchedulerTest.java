package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.dto.BroadcastStatus;
import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMessageRequestDto;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BroadcastSchedulerTest {

    @Mock
    BroadcastStateService broadcastStateService;

    @Mock
    ChatMessageService chatMessageService;

    @InjectMocks
    BroadcastScheduler scheduler;

    @Test
    void endAwayHosts_AnnouncesEachEndedBroadcastInItsChatAsItsHost() {
        BroadcastState ended = ended(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(broadcastStateService.endAwayHosts()).thenReturn(List.of(ended));

        scheduler.endAwayHosts();

        ArgumentCaptor<ChatMessageRequestDto> request = ArgumentCaptor.forClass(ChatMessageRequestDto.class);
        verify(chatMessageService).processMessage(request.capture(), eq(ended.hostId()));
        assertThat(request.getValue().chatId()).isEqualTo(ended.chatId());
        assertThat(request.getValue().content()).isEqualTo("[BROADCAST_NODE_TERMINATED]:" + ended.broadcastId());
        verify(broadcastStateService).forgetEnded();
    }

    @Test
    void endAwayHosts_AnnouncementFails_GoesOnWithTheOthers() {
        BroadcastState left = ended(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        BroadcastState other = ended(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(broadcastStateService.endAwayHosts()).thenReturn(List.of(left, other));
        // E.g. the host left the chat meanwhile
        doThrow(new UserNotAuthorizedException("not a member"))
                .when(chatMessageService).processMessage(any(), eq(left.hostId()));

        scheduler.endAwayHosts();

        verify(chatMessageService).processMessage(any(), eq(other.hostId()));
        verify(broadcastStateService).forgetEnded();
    }

    @Test
    void endAwayHosts_NothingEnded_AnnouncesNothing() {
        when(broadcastStateService.endAwayHosts()).thenReturn(List.of());

        scheduler.endAwayHosts();

        verify(chatMessageService, never()).processMessage(any(), any());
        verify(broadcastStateService).forgetEnded();
    }

    private static BroadcastState ended(UUID broadcastId, UUID chatId, UUID hostId) {
        return BroadcastState.builder()
                .broadcastId(broadcastId)
                .chatId(chatId)
                .hostId(hostId)
                .status(BroadcastStatus.TERMINATED)
                .build();
    }
}
