package com.romanosadchyi.smp.music_app.tabs.internal.service;

import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabUpdateRequest;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.AsciiTab;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.GuitarProTab;
import com.romanosadchyi.smp.music_app.tabs.internal.exception.TabNotFoundException;
import com.romanosadchyi.smp.music_app.tabs.internal.mapping.TabMappingService;
import com.romanosadchyi.smp.music_app.tabs.internal.parsing.AsciiTabParser;
import com.romanosadchyi.smp.music_app.tabs.internal.repository.TabRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TabUpdateServiceTest {

    @Mock
    TabRepository tabRepository;

    @Mock
    TabValidator tabValidator;

    @Mock
    AsciiTabParser asciiTabParser;

    @Mock
    TabMappingService tabMappingService;

    TabUpdateService tabUpdateService;

    UUID ownerId = UUID.randomUUID();
    UUID otherId = UUID.randomUUID();
    UUID tabId = UUID.randomUUID();

    @BeforeEach
    void createTheService() {
        tabUpdateService = new TabUpdateService(new TabResolver(tabRepository, List.of()), tabValidator,
                asciiTabParser, tabRepository, tabMappingService);
    }

    @Test
    void update_OwnAsciiTab_ReplacesMetadataAndText() {
        AsciiTab tab = asciiTab(ownerId, true);
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(tab));
        when(tabRepository.saveAndFlush(tab)).thenReturn(tab);

        tabUpdateService.update(ownerId, tabId, update("New", "Band", 90, "3/4", null, "B|---1---|"));

        assertThat(tab.getTitle()).isEqualTo("New");
        assertThat(tab.getArtist()).isEqualTo("Band");
        assertThat(tab.getTempoBpm()).isEqualTo(90);
        assertThat(tab.getTimeSignature()).isEqualTo("3/4");
        assertThat(tab.isPublic()).isFalse();
        assertThat(tab.getRawText()).isEqualTo("B|---1---|");
        verify(tabValidator).validateRawText("B|---1---|");
        verify(asciiTabParser).parse("B|---1---|");
    }

    @Test
    void update_AsciiTextIsNotATab_KeepsTheOldText() {
        AsciiTab tab = asciiTab(ownerId, true);
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(tab));
        when(asciiTabParser.parse("la")).thenThrow(new IllegalArgumentException("No tab found"));

        assertThatThrownBy(() -> tabUpdateService.update(ownerId, tabId, update("New", null, null, null, null, "la")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(tab.getRawText()).isEqualTo("e|---0---|");
        verify(tabRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_GuitarProTabWithText_Rejects() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(guitarProTab(ownerId, true)));

        assertThatThrownBy(() -> tabUpdateService.update(ownerId, tabId, update("New", null, null, null, null, "x")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tabRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_SomeoneElsesTab_IsForbidden() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(asciiTab(ownerId, true)));
        TabUpdateRequest request = update("New", null, null, null, null, "x");

        assertThatThrownBy(() -> tabUpdateService.update(otherId, tabId, request))
                .isInstanceOf(UserNotAuthorizedException.class);
        verify(tabRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_MissingTab_IsNotFound() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tabUpdateService.update(ownerId, tabId, update("a", null, null, null, null, null)))
                .isInstanceOf(TabNotFoundException.class);
    }

    private static TabUpdateRequest update(String title, String artist, Integer tempoBpm, String timeSignature,
                                           Boolean isPublic, String rawText) {
        return new TabUpdateRequest().title(title).artist(artist).tempoBpm(tempoBpm).timeSignature(timeSignature)
                .isPublic(isPublic).rawText(rawText);
    }

    private AsciiTab asciiTab(UUID owner, boolean isPublic) {
        return AsciiTab.builder().id(tabId).ownerId(owner).title("Riff").isPublic(isPublic).rightsConfirmed(true)
                .rawText("e|---0---|").build();
    }

    private GuitarProTab guitarProTab(UUID owner, boolean isPublic) {
        return GuitarProTab.builder().id(tabId).ownerId(owner).title("Song").isPublic(isPublic).rightsConfirmed(true)
                .fileKey("tabs/song.gp5").gpVersion("GP5").build();
    }
}
