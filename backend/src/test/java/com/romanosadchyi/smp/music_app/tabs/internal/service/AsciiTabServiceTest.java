package com.romanosadchyi.smp.music_app.tabs.internal.service;

import com.romanosadchyi.smp.music_app.tabs.internal.api.model.AsciiTabCreateRequest;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.AsciiTab;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.Tab;
import com.romanosadchyi.smp.music_app.tabs.internal.mapping.TabMappingService;
import com.romanosadchyi.smp.music_app.tabs.internal.parsing.AsciiTabParser;
import com.romanosadchyi.smp.music_app.tabs.internal.repository.TabRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsciiTabServiceTest {

    @Mock
    TabRepository tabRepository;

    @Mock
    TabValidator tabValidator;

    @Mock
    AsciiTabParser asciiTabParser;

    @Mock
    TabMappingService tabMappingService;

    @InjectMocks
    AsciiTabService asciiTabService;

    UUID ownerId = UUID.randomUUID();

    @Test
    void createAscii_Valid_ParsesTheTextAndSavesAPrivateTabByDefault() {
        AsciiTabCreateRequest request = new AsciiTabCreateRequest().title("Riff").rightsConfirmed(true)
                .rawText("e|---0---|");
        when(tabRepository.save(any(Tab.class))).thenAnswer(invocation -> invocation.getArgument(0));

        asciiTabService.createAscii(ownerId, request);

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

        assertThatThrownBy(() -> asciiTabService.createAscii(ownerId, request)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(tabRepository);
    }
}
