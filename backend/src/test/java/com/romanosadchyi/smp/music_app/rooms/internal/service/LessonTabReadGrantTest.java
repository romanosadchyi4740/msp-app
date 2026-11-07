package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.repository.LessonSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonTabReadGrantTest {

    @Mock
    LessonSessionRepository lessonSessionRepository;

    @InjectMocks
    LessonTabReadGrant grant;

    UUID tabId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    @Test
    void allowsRead_ActiveLessonTabOfTheParticipantsRoom_IsAllowed() {
        when(lessonSessionRepository.isActiveLessonTabOfParticipant(tabId, userId)).thenReturn(true);

        assertThat(grant.allowsRead(tabId, userId)).isTrue();
    }

    @Test
    void allowsRead_NoSuchLessonOrNotInTheRoom_IsRefused() {
        when(lessonSessionRepository.isActiveLessonTabOfParticipant(tabId, userId)).thenReturn(false);

        assertThat(grant.allowsRead(tabId, userId)).isFalse();
    }
}
