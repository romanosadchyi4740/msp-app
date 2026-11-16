package com.romanosadchyi.smp.music_app.tabs.internal.service;

import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.storage.external.FileStorage;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.AsciiTab;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.GuitarProTab;
import com.romanosadchyi.smp.music_app.tabs.internal.exception.TabNotFoundException;
import com.romanosadchyi.smp.music_app.tabs.internal.repository.TabRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TabDeletionServiceTest {

    @Mock
    TabRepository tabRepository;

    @Mock
    FileStorage fileStorage;

    TabDeletionService tabDeletionService;

    UUID ownerId = UUID.randomUUID();
    UUID otherId = UUID.randomUUID();
    UUID tabId = UUID.randomUUID();

    @BeforeEach
    void createTheService() {
        tabDeletionService = new TabDeletionService(new TabResolver(tabRepository, List.of()), tabRepository,
                fileStorage);
    }

    @Test
    void delete_OwnGuitarProTab_DeletesTheRowAndTheFile() {
        GuitarProTab tab = guitarProTab(ownerId, false);
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(tab));

        tabDeletionService.delete(ownerId, tabId);

        InOrder order = inOrder(tabRepository, fileStorage);
        order.verify(tabRepository).delete(tab);
        order.verify(tabRepository).flush();
        order.verify(fileStorage).delete("tabs/song.gp5");
    }

    @Test
    void delete_OwnAsciiTab_DeletesNoFile() {
        AsciiTab tab = asciiTab(ownerId, false);
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(tab));

        tabDeletionService.delete(ownerId, tabId);

        verify(tabRepository).delete(tab);
        verifyNoInteractions(fileStorage);
    }

    @Test
    void delete_SomeoneElsesTab_IsForbidden() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(asciiTab(ownerId, true)));

        assertThatThrownBy(() -> tabDeletionService.delete(otherId, tabId))
                .isInstanceOf(UserNotAuthorizedException.class);
        verify(tabRepository, never()).delete(any());
        verifyNoInteractions(fileStorage);
    }

    @Test
    void delete_MissingTab_IsNotFound() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tabDeletionService.delete(ownerId, tabId)).isInstanceOf(TabNotFoundException.class);
        verifyNoInteractions(fileStorage);
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
