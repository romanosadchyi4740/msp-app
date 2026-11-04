package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.LessonTabRequest;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.LessonTabNotFoundException;
import com.romanosadchyi.smp.music_app.tabs.external.TabLookup;
import com.romanosadchyi.smp.music_app.tabs.external.TabSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonTabResolverTest {

    @Mock
    TabLookup tabLookup;

    @InjectMocks
    LessonTabResolver lessonTabResolver;

    UUID tabId = UUID.randomUUID();
    UUID hostId = UUID.randomUUID();

    @Test
    void requireTabId_MissingRequestOrTab_IsRejected() {
        assertThatThrownBy(() -> lessonTabResolver.requireTabId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lessonTabResolver.requireTabId(new LessonTabRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A tab is required");
        assertThat(lessonTabResolver.requireTabId(new LessonTabRequest().tabId(tabId))).isEqualTo(tabId);
    }

    @Test
    void readableTab_TabTheHostMayNotRead_IsNotFound() {
        when(tabLookup.findReadable(tabId, hostId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lessonTabResolver.readableTab(tabId, hostId))
                .isInstanceOf(LessonTabNotFoundException.class);
    }

    @Test
    void readableTab_Readable_IsReturned() {
        TabSummary tab = new TabSummary(tabId, "Etude", 72, "3/4");
        when(tabLookup.findReadable(tabId, hostId)).thenReturn(Optional.of(tab));

        assertThat(lessonTabResolver.readableTab(tabId, hostId)).isSameAs(tab);
    }

    @Test
    void tempoOf_TheTabsTempoOr120() {
        assertThat(LessonTabResolver.tempoOf(new TabSummary(tabId, "Etude", 72, null))).isEqualTo(72);
        assertThat(LessonTabResolver.tempoOf(new TabSummary(tabId, "Etude", null, null))).isEqualTo(120);
    }
}
