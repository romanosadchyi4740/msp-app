package com.romanosadchyi.smp.music_app.tabs.internal.service;

import com.romanosadchyi.smp.music_app.tabs.internal.api.model.AsciiTabPreview;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.AsciiTabPreviewRequest;
import com.romanosadchyi.smp.music_app.tabs.internal.parsing.AlphaTexWriter;
import com.romanosadchyi.smp.music_app.tabs.internal.parsing.AsciiTabParser;
import com.romanosadchyi.smp.music_app.tabs.internal.parsing.AsciiTabScore;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

/**
 * Turns ASCII tab text into alphaTex for the editor's preview, without storing anything.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AsciiTabPreviewService {

    TabValidator tabValidator;
    AsciiTabParser asciiTabParser;
    AlphaTexWriter alphaTexWriter;

    /**
     * The alphaTex of ASCII tab text, for the editor's preview; nothing is stored.
     *
     * @throws IllegalArgumentException if the text is missing, too long or not a tab, or the rhythm is invalid
     */
    public AsciiTabPreview previewAscii(AsciiTabPreviewRequest request) {
        String title = TabText.stripToNull(request.getTitle());
        String artist = TabText.stripToNull(request.getArtist());
        tabValidator.validateRawText(request.getRawText());
        tabValidator.validateTextLengths(title, artist);
        tabValidator.validateRhythm(request.getTempoBpm(), request.getTimeSignature());
        AsciiTabScore score = asciiTabParser.parse(request.getRawText());
        String alphaTex = alphaTexWriter.write(score, title, artist, request.getTempoBpm(), request.getTimeSignature());
        return new AsciiTabPreview(alphaTex, score.measures().size());
    }
}
