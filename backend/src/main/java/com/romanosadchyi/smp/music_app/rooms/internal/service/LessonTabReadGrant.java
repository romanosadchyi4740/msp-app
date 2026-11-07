package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.repository.LessonSessionRepository;
import com.romanosadchyi.smp.music_app.tabs.external.TabReadGrant;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Lets the participants of a room read the tab of its active lesson (KAN-94), which may be the host's private tab:
 * everyone in the lesson sees the same tab (UR-3). The access lasts as long as both do: once the lesson ends, or the
 * participant leaves the room, the tab is private again.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class LessonTabReadGrant implements TabReadGrant {

    LessonSessionRepository lessonSessionRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean allowsRead(UUID tabId, UUID userId) {
        return lessonSessionRepository.isActiveLessonTabOfParticipant(tabId, userId);
    }
}
