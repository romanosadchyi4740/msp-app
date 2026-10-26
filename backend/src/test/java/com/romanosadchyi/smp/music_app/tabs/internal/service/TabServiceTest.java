package com.romanosadchyi.smp.music_app.tabs.internal.service;

import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.storage.external.FileStorage;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.AsciiTabCreateRequest;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.AsciiTabPreview;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.AsciiTabPreviewRequest;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabResponse;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabUpdateRequest;
import com.romanosadchyi.smp.music_app.tabs.internal.dto.GuitarProTabUploadDto;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.AsciiTab;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.GuitarProTab;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.Tab;
import com.romanosadchyi.smp.music_app.tabs.internal.exception.TabNotFoundException;
import com.romanosadchyi.smp.music_app.tabs.internal.mapping.TabMappingService;
import com.romanosadchyi.smp.music_app.tabs.internal.parsing.AlphaTexWriter;
import com.romanosadchyi.smp.music_app.tabs.internal.parsing.AsciiMeasure;
import com.romanosadchyi.smp.music_app.tabs.internal.parsing.AsciiTabParser;
import com.romanosadchyi.smp.music_app.tabs.internal.parsing.AsciiTabScore;
import com.romanosadchyi.smp.music_app.tabs.internal.repository.TabRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TabServiceTest {

    @Mock
    TabRepository tabRepository;

    @Mock
    TabValidator tabValidator;

    @Mock
    TabMappingService tabMappingService;

    @Mock
    AsciiTabParser asciiTabParser;

    @Mock
    AlphaTexWriter alphaTexWriter;

    @Mock
    FileStorage fileStorage;

    @InjectMocks
    TabService tabService;

    UUID ownerId = UUID.randomUUID();
    UUID otherId = UUID.randomUUID();
    UUID tabId = UUID.randomUUID();

    @Test
    void uploadGuitarPro_Valid_SavesTheTabBeforeStoringTheFile() {
        MockMultipartFile file = new MockMultipartFile("file", "song.gp5", null, new byte[]{1});
        GuitarProTabUploadDto dto = new GuitarProTabUploadDto(" Song ", "  ", 120, "4/4", true, true);
        when(tabValidator.validateGuitarProFile(file)).thenReturn("gp5");
        when(tabRepository.saveAndFlush(any(Tab.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TabResponse response = new TabResponse();
        when(tabMappingService.map(any(Tab.class))).thenReturn(response);

        assertThat(tabService.uploadGuitarPro(ownerId, dto, file)).isSameAs(response);

        ArgumentCaptor<GuitarProTab> saved = ArgumentCaptor.forClass(GuitarProTab.class);
        InOrder order = inOrder(tabValidator, tabRepository, fileStorage);
        order.verify(tabValidator).requireRightsConfirmed(true);
        order.verify(tabValidator).validateMetadata("Song", null, 120, "4/4");
        order.verify(tabRepository).saveAndFlush(saved.capture());
        GuitarProTab tab = saved.getValue();
        order.verify(fileStorage).upload(file, tab.getFileKey());
        assertThat(tab.getFileKey()).matches("tabs/[0-9a-f-]{36}\\.gp5");
        assertThat(tab.getGpVersion()).isEqualTo("GP5");
        assertThat(tab.getTitle()).isEqualTo("Song");
        assertThat(tab.getArtist()).isNull();
        assertThat(tab.getOwnerId()).isEqualTo(ownerId);
        assertThat(tab.isPublic()).isTrue();
        assertThat(tab.isRightsConfirmed()).isTrue();
    }

    @Test
    void uploadGuitarPro_RightsNotConfirmed_StoresNothing() {
        GuitarProTabUploadDto dto = new GuitarProTabUploadDto("Song", null, null, null, null, false);
        doThrow(new IllegalArgumentException("rights")).when(tabValidator).requireRightsConfirmed(false);

        assertThatThrownBy(() -> tabService.uploadGuitarPro(ownerId, dto, mock(MockMultipartFile.class)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(tabRepository, fileStorage);
    }

    @Test
    void createAscii_Valid_ParsesTheTextAndSavesAPrivateTabByDefault() {
        AsciiTabCreateRequest request = new AsciiTabCreateRequest().title("Riff").rightsConfirmed(true)
                .rawText("e|---0---|");
        when(tabRepository.save(any(Tab.class))).thenAnswer(invocation -> invocation.getArgument(0));

        tabService.createAscii(ownerId, request);

        ArgumentCaptor<AsciiTab> saved = ArgumentCaptor.forClass(AsciiTab.class);
        InOrder order = inOrder(tabValidator, asciiTabParser, tabRepository);
        order.verify(tabValidator).requireRightsConfirmed(true);
        order.verify(tabValidator).validateRawText("e|---0---|");
        order.verify(asciiTabParser).parse("e|---0---|");
        order.verify(tabRepository).save(saved.capture());
        assertThat(saved.getValue().getRawText()).isEqualTo("e|---0---|");
        assertThat(saved.getValue().isPublic()).isFalse();
        verify(tabMappingService).map(saved.getValue());
    }

    @Test
    void createAscii_TextIsNotATab_StoresNothing() {
        AsciiTabCreateRequest request = new AsciiTabCreateRequest().title("Lyrics").rightsConfirmed(true)
                .rawText("la la la");
        when(asciiTabParser.parse("la la la")).thenThrow(new IllegalArgumentException("No tab found"));

        assertThatThrownBy(() -> tabService.createAscii(ownerId, request)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(tabRepository);
    }

    @Test
    void previewAscii_Tab_ReturnsItsAlphaTexAndMeasureCountWithoutStoringAnything() {
        AsciiTabPreviewRequest request = new AsciiTabPreviewRequest().rawText("e|-0-|").title(" Riff ").artist("  ")
                .tempoBpm(90).timeSignature("3/4");
        AsciiTabScore score = new AsciiTabScore(List.of("E4"),
                List.of(new AsciiMeasure(List.of(), false, 0), new AsciiMeasure(List.of(), false, 0)));
        when(asciiTabParser.parse("e|-0-|")).thenReturn(score);
        when(alphaTexWriter.write(score, "Riff", null, 90, "3/4")).thenReturn("tex");

        AsciiTabPreview preview = tabService.previewAscii(request);

        assertThat(preview.getAlphaTex()).isEqualTo("tex");
        assertThat(preview.getMeasureCount()).isEqualTo(2);
        verify(tabValidator).validateRawText("e|-0-|");
        verify(tabValidator).validateTextLengths("Riff", null);
        verify(tabValidator).validateRhythm(90, "3/4");
        verifyNoInteractions(tabRepository, fileStorage);
    }

    @Test
    void previewAscii_ValidatorRejects_DoesNotParse() {
        AsciiTabPreviewRequest request = new AsciiTabPreviewRequest().rawText(" ");
        doThrow(new IllegalArgumentException("Tab text is required")).when(tabValidator).validateRawText(" ");

        assertThatThrownBy(() -> tabService.previewAscii(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(asciiTabParser, alphaTexWriter);
    }

    @Test
    void previewAscii_TextIsNotATab_Rejects() {
        when(asciiTabParser.parse("la")).thenThrow(new IllegalArgumentException("No tab found"));

        assertThatThrownBy(() -> tabService.previewAscii(new AsciiTabPreviewRequest().rawText("la")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(alphaTexWriter);
    }

    @Test
    void update_OwnAsciiTab_ReplacesMetadataAndText() {
        AsciiTab tab = asciiTab(ownerId, true);
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(tab));
        when(tabRepository.saveAndFlush(tab)).thenReturn(tab);

        tabService.update(ownerId, tabId, update("New", "Band", 90, "3/4", null, "B|---1---|"));

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

        assertThatThrownBy(() -> tabService.update(ownerId, tabId, update("New", null, null, null, null, "la")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(tab.getRawText()).isEqualTo("e|---0---|");
        verify(tabRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_GuitarProTabWithText_Rejects() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(guitarProTab(ownerId, true)));

        assertThatThrownBy(() -> tabService.update(ownerId, tabId, update("New", null, null, null, null, "x")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tabRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateAndDelete_SomeoneElsesTab_AreForbidden() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(asciiTab(ownerId, true)));
        TabUpdateRequest request = update("New", null, null, null, null, "x");

        assertThatThrownBy(() -> tabService.update(otherId, tabId, request)).isInstanceOf(UserNotAuthorizedException.class);
        assertThatThrownBy(() -> tabService.delete(otherId, tabId)).isInstanceOf(UserNotAuthorizedException.class);
        verify(tabRepository, never()).saveAndFlush(any());
        verify(tabRepository, never()).delete(any());
    }

    @Test
    void updateAndDelete_MissingTab_AreNotFound() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tabService.update(ownerId, tabId, update("a", null, null, null, null, null)))
                .isInstanceOf(TabNotFoundException.class);
        assertThatThrownBy(() -> tabService.delete(ownerId, tabId)).isInstanceOf(TabNotFoundException.class);
    }

    @Test
    void delete_OwnGuitarProTab_DeletesTheRowAndTheFile() {
        GuitarProTab tab = guitarProTab(ownerId, false);
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(tab));

        tabService.delete(ownerId, tabId);

        InOrder order = inOrder(tabRepository, fileStorage);
        order.verify(tabRepository).delete(tab);
        order.verify(tabRepository).flush();
        order.verify(fileStorage).delete("tabs/song.gp5");
    }

    @Test
    void delete_OwnAsciiTab_DeletesNoFile() {
        AsciiTab tab = asciiTab(ownerId, false);
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(tab));

        tabService.delete(ownerId, tabId);

        verify(tabRepository).delete(tab);
        verifyNoInteractions(fileStorage);
    }

    @Test
    void getTab_OwnPrivateOrOthersPublic_IsReturned() {
        AsciiTab own = asciiTab(ownerId, false);
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(own));
        tabService.getTab(ownerId, tabId);

        AsciiTab othersPublic = asciiTab(ownerId, true);
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(othersPublic));
        tabService.getTab(otherId, tabId);

        verify(tabMappingService).map(own);
        verify(tabMappingService).map(othersPublic);
    }

    @Test
    void getTab_OthersPrivateOrHidden_IsNotFound() {
        AsciiTab hidden = asciiTab(ownerId, true);
        hidden.setHidden(true);

        when(tabRepository.findById(tabId)).thenReturn(Optional.of(asciiTab(ownerId, false)));
        assertThatThrownBy(() -> tabService.getTab(otherId, tabId)).isInstanceOf(TabNotFoundException.class);
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(hidden));
        assertThatThrownBy(() -> tabService.getTab(otherId, tabId)).isInstanceOf(TabNotFoundException.class);
        verifyNoInteractions(tabMappingService);
    }

    @Test
    void getTab_OwnHiddenTab_IsReturned() {
        AsciiTab hidden = asciiTab(ownerId, true);
        hidden.setHidden(true);
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(hidden));

        tabService.getTab(ownerId, tabId);

        verify(tabMappingService).map(hidden);
    }

    @Test
    void getFileUrl_ReadableGuitarProTab_ReturnsAPresignedUrl() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(guitarProTab(ownerId, true)));
        when(fileStorage.presignedGetUrl("tabs/song.gp5")).thenReturn("https://storage/signed");

        assertThat(tabService.getFileUrl(otherId, tabId)).isEqualTo("https://storage/signed");
    }

    @Test
    void getFileUrl_AsciiTab_Rejects() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(asciiTab(ownerId, true)));

        assertThatThrownBy(() -> tabService.getFileUrl(ownerId, tabId)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(fileStorage);
    }

    @Test
    void getFileUrl_OthersPrivateTab_IsNotFound() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(guitarProTab(ownerId, false)));

        assertThatThrownBy(() -> tabService.getFileUrl(otherId, tabId)).isInstanceOf(TabNotFoundException.class);
        verifyNoInteractions(fileStorage);
    }

    @Test
    void getMyTabs_MapsTheOwnersTabsInOneBatch() {
        List<Tab> tabs = List.of(asciiTab(ownerId, false));
        when(tabRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId)).thenReturn(tabs);

        tabService.getMyTabs(ownerId);

        verify(tabMappingService).mapAll(tabs);
    }

    @Test
    void search_StripsTheQueryAndSortsNewestFirst() {
        Page<Tab> tabs = new PageImpl<>(List.of());
        when(tabRepository.searchPublic(eq("blues"), any(Pageable.class))).thenReturn(tabs);

        tabService.search("  blues ", 1, 5);

        verify(tabRepository).searchPublic("blues", PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "createdAt")));
        verify(tabMappingService).mapPage(tabs);
    }

    @Test
    void search_WildcardsInQuery_AreEscapedAndThePageSizeIsCapped() {
        when(tabRepository.searchPublic(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        tabService.search("50%_off\\", 0, 10_000);

        verify(tabRepository).searchPublic("50\\%\\_off\\\\",
                PageRequest.of(0, TabService.MAX_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Test
    void search_NoQuery_SearchesForEverything() {
        when(tabRepository.searchPublic(eq(""), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        tabService.search(null, 0, 20);

        verify(tabRepository).searchPublic(eq(""), any(Pageable.class));
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
