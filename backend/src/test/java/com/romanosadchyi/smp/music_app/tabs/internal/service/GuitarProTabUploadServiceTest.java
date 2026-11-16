package com.romanosadchyi.smp.music_app.tabs.internal.service;

import com.romanosadchyi.smp.music_app.storage.external.FileStorage;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabResponse;
import com.romanosadchyi.smp.music_app.tabs.internal.dto.GuitarProTabUploadDto;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.GuitarProTab;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.Tab;
import com.romanosadchyi.smp.music_app.tabs.internal.mapping.TabMappingService;
import com.romanosadchyi.smp.music_app.tabs.internal.repository.TabRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuitarProTabUploadServiceTest {

    @Mock
    TabRepository tabRepository;

    @Mock
    TabValidator tabValidator;

    @Mock
    FileStorage fileStorage;

    @Mock
    TabMappingService tabMappingService;

    @InjectMocks
    GuitarProTabUploadService guitarProTabUploadService;

    UUID ownerId = UUID.randomUUID();

    @Test
    void uploadGuitarPro_Valid_SavesTheTabBeforeStoringTheFile() {
        MockMultipartFile file = new MockMultipartFile("file", "song.gp5", null, new byte[]{1});
        GuitarProTabUploadDto dto = new GuitarProTabUploadDto(" Song ", "  ", 120, "4/4", true, true);
        when(tabValidator.validateGuitarProFile(file)).thenReturn("gp5");
        when(tabRepository.saveAndFlush(any(Tab.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TabResponse response = new TabResponse();
        when(tabMappingService.map(any(Tab.class))).thenReturn(response);

        assertThat(guitarProTabUploadService.uploadGuitarPro(ownerId, dto, file)).isSameAs(response);

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

        assertThatThrownBy(() -> guitarProTabUploadService.uploadGuitarPro(ownerId, dto, mock(MockMultipartFile.class)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(tabRepository, fileStorage);
    }
}
