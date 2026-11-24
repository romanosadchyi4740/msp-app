package com.romanosadchyi.smp.music_app.tabs.internal.service;

import com.romanosadchyi.smp.music_app.storage.external.FileStorage;
import com.romanosadchyi.smp.music_app.tabs.external.TabReadGrant;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.AsciiTab;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.GuitarProTab;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.Tab;
import com.romanosadchyi.smp.music_app.tabs.internal.exception.TabNotFoundException;
import com.romanosadchyi.smp.music_app.tabs.internal.mapping.TabMappingService;
import com.romanosadchyi.smp.music_app.tabs.internal.repository.TabRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TabQueryServiceTest {

    @Mock
    TabRepository tabRepository;

    @Mock
    FileStorage fileStorage;

    @Mock
    TabMappingService tabMappingService;

    @Mock
    TabReadGrant tabReadGrant;

    TabQueryService tabQueryService;

    UUID ownerId = UUID.randomUUID();
    UUID otherId = UUID.randomUUID();
    UUID tabId = UUID.randomUUID();

    @BeforeEach
    void createTheService() {
        tabQueryService = new TabQueryService(new TabResolver(tabRepository, List.of(tabReadGrant)),
                tabRepository, fileStorage, tabMappingService);
    }

    @Test
    void getTab_OwnPrivateOrOthersPublic_IsReturned() {
        AsciiTab own = asciiTab(ownerId, false);
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(own));
        tabQueryService.getTab(ownerId, tabId);

        AsciiTab othersPublic = asciiTab(ownerId, true);
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(othersPublic));
        tabQueryService.getTab(otherId, tabId);

        verify(tabMappingService).map(own);
        verify(tabMappingService).map(othersPublic);
    }

    @Test
    void getTab_OthersPrivateOrHidden_IsNotFound() {
        AsciiTab hidden = asciiTab(ownerId, true);
        hidden.setHidden(true);

        when(tabRepository.findById(tabId)).thenReturn(Optional.of(asciiTab(ownerId, false)));
        assertThatThrownBy(() -> tabQueryService.getTab(otherId, tabId)).isInstanceOf(TabNotFoundException.class);
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(hidden));
        assertThatThrownBy(() -> tabQueryService.getTab(otherId, tabId)).isInstanceOf(TabNotFoundException.class);
        verifyNoInteractions(tabMappingService);
    }

    @Test
    void getTab_OthersPrivateTab_IsReturnedWhenAGrantAllowsIt() {
        AsciiTab lessonTab = asciiTab(ownerId, false);
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(lessonTab));
        when(tabReadGrant.allowsRead(tabId, otherId)).thenReturn(true);

        tabQueryService.getTab(otherId, tabId);

        verify(tabMappingService).map(lessonTab);
    }

    @Test
    void getTab_OthersPrivateTab_IsNotFoundWhenNoGrantAllowsIt() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(asciiTab(ownerId, false)));
        when(tabReadGrant.allowsRead(tabId, otherId)).thenReturn(false);

        assertThatThrownBy(() -> tabQueryService.getTab(otherId, tabId)).isInstanceOf(TabNotFoundException.class);
        verify(tabReadGrant).allowsRead(tabId, otherId);
        verifyNoInteractions(tabMappingService);
    }

    @Test
    void getTab_OthersHiddenTab_StaysHiddenWhateverTheGrants() {
        AsciiTab hidden = asciiTab(ownerId, false);
        hidden.setHidden(true);
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(hidden));

        assertThatThrownBy(() -> tabQueryService.getTab(otherId, tabId)).isInstanceOf(TabNotFoundException.class);
        verifyNoInteractions(tabReadGrant);
    }

    @Test
    void getTab_OwnOrPublicTab_DoesNotAskTheGrants() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(asciiTab(ownerId, true)));

        tabQueryService.getTab(otherId, tabId);
        tabQueryService.getTab(ownerId, tabId);

        verifyNoInteractions(tabReadGrant);
    }

    @Test
    void getFileUrl_OthersPrivateGuitarProTab_IsSignedWhenAGrantAllowsIt() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(guitarProTab(ownerId, false)));
        when(tabReadGrant.allowsRead(tabId, otherId)).thenReturn(true);
        when(fileStorage.presignedGetUrl("tabs/song.gp5")).thenReturn("https://storage/signed");

        assertThat(tabQueryService.getFileUrl(otherId, tabId)).isEqualTo("https://storage/signed");
    }

    @Test
    void getTab_OwnHiddenTab_IsReturned() {
        AsciiTab hidden = asciiTab(ownerId, true);
        hidden.setHidden(true);
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(hidden));

        tabQueryService.getTab(ownerId, tabId);

        verify(tabMappingService).map(hidden);
    }

    @Test
    void getFileUrl_ReadableGuitarProTab_ReturnsAPresignedUrl() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(guitarProTab(ownerId, true)));
        when(fileStorage.presignedGetUrl("tabs/song.gp5")).thenReturn("https://storage/signed");

        assertThat(tabQueryService.getFileUrl(otherId, tabId)).isEqualTo("https://storage/signed");
    }

    @Test
    void getFileUrl_AsciiTab_Rejects() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(asciiTab(ownerId, true)));

        assertThatThrownBy(() -> tabQueryService.getFileUrl(ownerId, tabId)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(fileStorage);
    }

    @Test
    void getFileUrl_OthersPrivateTab_IsNotFound() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(guitarProTab(ownerId, false)));

        assertThatThrownBy(() -> tabQueryService.getFileUrl(otherId, tabId)).isInstanceOf(TabNotFoundException.class);
        verifyNoInteractions(fileStorage);
    }

    @Test
    void getMyTabs_MapsTheOwnersTabsInOneBatch() {
        List<Tab> tabs = List.of(asciiTab(ownerId, false));
        when(tabRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId)).thenReturn(tabs);

        tabQueryService.getMyTabs(ownerId);

        verify(tabMappingService).mapAll(tabs);
    }

    @Test
    void search_StripsTheQueryAndSortsNewestFirst() {
        Page<Tab> tabs = new PageImpl<>(List.of());
        when(tabRepository.searchPublic(eq("blues"), any(Pageable.class))).thenReturn(tabs);

        tabQueryService.search("  blues ", 1, 5);

        verify(tabRepository).searchPublic("blues", PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "createdAt")));
        verify(tabMappingService).mapPage(tabs);
    }

    @Test
    void search_WildcardsInQuery_AreEscapedAndThePageSizeIsCapped() {
        when(tabRepository.searchPublic(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        tabQueryService.search("50%_off\\", 0, 10_000);

        verify(tabRepository).searchPublic("50\\%\\_off\\\\",
                PageRequest.of(0, TabQueryService.MAX_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Test
    void search_NoQuery_SearchesForEverything() {
        when(tabRepository.searchPublic(eq(""), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        tabQueryService.search(null, 0, 20);

        verify(tabRepository).searchPublic(eq(""), any(Pageable.class));
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
