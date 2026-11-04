package com.romanosadchyi.smp.music_app.rooms.internal.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonLimitSchedulerTest {

    @Mock
    LessonLimitService lessonLimitService;

    @InjectMocks
    LessonLimitScheduler lessonLimitScheduler;

    @Test
    void checkLimits_ChecksEveryDueRoom_EvenAfterOneFails() {
        UUID failing = UUID.randomUUID();
        UUID next = UUID.randomUUID();
        when(lessonLimitService.findRoomsDueForLimitCheck()).thenReturn(List.of(failing, next));
        doThrow(new IllegalStateException("boom")).when(lessonLimitService).checkLimit(failing);

        lessonLimitScheduler.checkLimits();

        verify(lessonLimitService).checkLimit(failing);
        verify(lessonLimitService).checkLimit(next);
    }
}
