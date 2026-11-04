package com.romanosadchyi.smp.music_app.tabs.internal.service;

import com.romanosadchyi.smp.music_app.tabs.external.TabSummary;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.AsciiTab;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.Tab;
import com.romanosadchyi.smp.music_app.tabs.internal.repository.TabRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TabLookupServiceTest {

    @Mock
    TabRepository tabRepository;

    @InjectMocks
    TabLookupService tabLookupService;

    UUID ownerId = UUID.randomUUID();
    UUID otherId = UUID.randomUUID();
    UUID tabId = UUID.randomUUID();

    @Test
    void findReadable_OwnPrivateTab_IsFoundWithItsTempo() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(tab(false, false)));

        assertThat(tabLookupService.findReadable(tabId, ownerId))
                .contains(new TabSummary(tabId, "Etude", 72, "3/4"));
    }

    @Test
    void findReadable_OthersPublicTab_IsFound() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(tab(true, false)));

        assertThat(tabLookupService.findReadable(tabId, otherId)).isPresent();
    }

    @Test
    void findReadable_OthersPrivateOrHiddenTab_IsEmpty() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(tab(false, false)), Optional.of(tab(true, true)));

        assertThat(tabLookupService.findReadable(tabId, otherId)).isEmpty();
        assertThat(tabLookupService.findReadable(tabId, otherId)).isEmpty();
    }

    @Test
    void findReadable_MissingTab_IsEmpty() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.empty());

        assertThat(tabLookupService.findReadable(tabId, ownerId)).isEmpty();
    }

    private Tab tab(boolean isPublic, boolean hidden) {
        return AsciiTab.builder()
                .id(tabId)
                .ownerId(ownerId)
                .title("Etude")
                .tempoBpm(72)
                .timeSignature("3/4")
                .isPublic(isPublic)
                .hidden(hidden)
                .rightsConfirmed(true)
                .build();
    }
}
