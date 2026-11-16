package com.romanosadchyi.smp.music_app.tabs.internal.service;

import com.romanosadchyi.smp.music_app.tabs.internal.api.model.AsciiTabPreview;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.AsciiTabPreviewRequest;
import com.romanosadchyi.smp.music_app.tabs.internal.parsing.AlphaTexWriter;
import com.romanosadchyi.smp.music_app.tabs.internal.parsing.AsciiMeasure;
import com.romanosadchyi.smp.music_app.tabs.internal.parsing.AsciiTabParser;
import com.romanosadchyi.smp.music_app.tabs.internal.parsing.AsciiTabScore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsciiTabPreviewServiceTest {

    @Mock
    TabValidator tabValidator;

    @Mock
    AsciiTabParser asciiTabParser;

    @Mock
    AlphaTexWriter alphaTexWriter;

    @InjectMocks
    AsciiTabPreviewService asciiTabPreviewService;

    @Test
    void previewAscii_Tab_ReturnsItsAlphaTexAndMeasureCount() {
        AsciiTabPreviewRequest request = new AsciiTabPreviewRequest().rawText("e|-0-|").title(" Riff ").artist("  ")
                .tempoBpm(90).timeSignature("3/4");
        AsciiTabScore score = new AsciiTabScore(List.of("E4"),
                List.of(new AsciiMeasure(List.of(), false, 0), new AsciiMeasure(List.of(), false, 0)));
        when(asciiTabParser.parse("e|-0-|")).thenReturn(score);
        when(alphaTexWriter.write(score, "Riff", null, 90, "3/4")).thenReturn("tex");

        AsciiTabPreview preview = asciiTabPreviewService.previewAscii(request);

        assertThat(preview.getAlphaTex()).isEqualTo("tex");
        assertThat(preview.getMeasureCount()).isEqualTo(2);
        verify(tabValidator).validateRawText("e|-0-|");
        verify(tabValidator).validateTextLengths("Riff", null);
        verify(tabValidator).validateRhythm(90, "3/4");
    }

    @Test
    void previewAscii_ValidatorRejects_DoesNotParse() {
        AsciiTabPreviewRequest request = new AsciiTabPreviewRequest().rawText(" ");
        doThrow(new IllegalArgumentException("Tab text is required")).when(tabValidator).validateRawText(" ");

        assertThatThrownBy(() -> asciiTabPreviewService.previewAscii(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(asciiTabParser, alphaTexWriter);
    }

    @Test
    void previewAscii_TextIsNotATab_Rejects() {
        when(asciiTabParser.parse("la")).thenThrow(new IllegalArgumentException("No tab found"));

        assertThatThrownBy(() -> asciiTabPreviewService.previewAscii(new AsciiTabPreviewRequest().rawText("la")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(alphaTexWriter);
    }
}
