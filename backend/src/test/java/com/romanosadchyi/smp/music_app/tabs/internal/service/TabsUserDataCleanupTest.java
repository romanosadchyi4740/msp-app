package com.romanosadchyi.smp.music_app.tabs.internal.service;

import com.romanosadchyi.smp.music_app.storage.external.FileStorage;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.AsciiTab;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.GuitarProTab;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.Tab;
import com.romanosadchyi.smp.music_app.tabs.internal.repository.TabRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TabsUserDataCleanupTest {

    @Mock
    TabRepository tabRepository;

    @Mock
    FileStorage fileStorage;

    @InjectMocks
    TabsUserDataCleanup cleanup;

    @Test
    void deleteUserData_GuitarProAndAsciiTabs_DeletesTabsAndOnlyGuitarProFiles() {
        UUID userId = UUID.randomUUID();
        GuitarProTab guitarProTab = GuitarProTab.builder().id(UUID.randomUUID()).fileKey("tabs/song.gp5").build();
        AsciiTab asciiTab = AsciiTab.builder().id(UUID.randomUUID()).rawText("e|---0---|").build();
        List<Tab> tabs = List.of(guitarProTab, asciiTab);
        when(tabRepository.findAllByOwnerId(userId)).thenReturn(tabs);

        cleanup.deleteUserData(userId);

        InOrder order = inOrder(tabRepository, fileStorage);
        order.verify(tabRepository).deleteAll(tabs);
        order.verify(tabRepository).flush();
        order.verify(fileStorage).delete("tabs/song.gp5");
    }

    @Test
    void deleteUserData_NoTabs_DeletesNoFiles() {
        UUID userId = UUID.randomUUID();
        when(tabRepository.findAllByOwnerId(userId)).thenReturn(List.of());

        cleanup.deleteUserData(userId);

        verifyNoInteractions(fileStorage);
        verify(tabRepository).deleteAll(List.of());
    }
}
