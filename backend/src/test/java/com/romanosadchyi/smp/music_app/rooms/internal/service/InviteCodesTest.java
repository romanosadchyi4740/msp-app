package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InviteCodesTest {

    @Mock
    InviteCodeGenerator inviteCodeGenerator;

    @Mock
    RoomRepository roomRepository;

    @InjectMocks
    InviteCodes inviteCodes;

    @Test
    void newCode_SkipsTakenCodes() {
        when(inviteCodeGenerator.generate()).thenReturn("Taken0000000", "Free00000000");
        when(roomRepository.existsByInviteCode("Taken0000000")).thenReturn(true);
        when(roomRepository.existsByInviteCode("Free00000000")).thenReturn(false);

        assertThat(inviteCodes.newCode()).isEqualTo("Free00000000");
    }

    @Test
    void newCode_EveryAttemptTaken_GivesUp() {
        when(inviteCodeGenerator.generate()).thenReturn("Taken0000000");
        when(roomRepository.existsByInviteCode(any())).thenReturn(true);

        assertThatThrownBy(() -> inviteCodes.newCode()).isInstanceOf(IllegalStateException.class);
        verify(inviteCodeGenerator, times(InviteCodes.MAX_ATTEMPTS)).generate();
    }
}
