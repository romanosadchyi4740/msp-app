package com.romanosadchyi.smp.music_app.tabs.internal.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TabValidatorTest {

    TabValidator validator = new TabValidator();

    @Test
    void requireRightsConfirmed_NotTrue_Rejects() {
        assertThatThrownBy(() -> validator.requireRightsConfirmed(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.requireRightsConfirmed(false)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> validator.requireRightsConfirmed(true)).doesNotThrowAnyException();
    }

    @Test
    void validateMetadata_ValidOrOptionalFieldsMissing_Accepts() {
        assertThatCode(() -> validator.validateMetadata("Song", "Artist", 120, "6/8")).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateMetadata("Song", null, null, null)).doesNotThrowAnyException();
    }

    @Test
    void validateMetadata_BlankOrTooLongTitleOrTooLongArtist_Rejects() {
        assertThatThrownBy(() -> validator.validateMetadata(" ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Title is required");
        assertThatThrownBy(() -> validator.validateMetadata("a".repeat(256), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validateMetadata("Song", "a".repeat(256), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateMetadata_TempoNotPositive_Rejects() {
        assertThatThrownBy(() -> validator.validateMetadata("Song", null, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateRhythm_TempoNotPositiveOrMalformedTimeSignature_Rejects() {
        assertThatThrownBy(() -> validator.validateRhythm(-1, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validateRhythm(null, "3")).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> validator.validateRhythm(null, null)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateRhythm(60, "12/8")).doesNotThrowAnyException();
    }

    @Test
    void validateTextLengths_TooLong_RejectsAndMissingIsFine() {
        assertThatThrownBy(() -> validator.validateTextLengths("a".repeat(256), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validateTextLengths(null, "a".repeat(256)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> validator.validateTextLengths(null, null)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"4", "4/3", "0/4", "4/4/4", "four/four", "4/ 4"})
    void validateMetadata_MalformedTimeSignature_Rejects(String timeSignature) {
        assertThatThrownBy(() -> validator.validateMetadata("Song", null, null, timeSignature))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateRawText_BlankOrTooLong_Rejects() {
        assertThatThrownBy(() -> validator.validateRawText(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validateRawText("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validateRawText("-".repeat(TabValidator.MAX_RAW_TEXT_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> validator.validateRawText("-".repeat(TabValidator.MAX_RAW_TEXT_LENGTH)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"song.gp", "song.GP5", "song.gpx"})
    void validateGuitarProFile_SupportedExtensionAndMatchingContent_ReturnsTheExtensionInLowerCase(String filename) {
        String expected = filename.substring(filename.indexOf('.') + 1).toLowerCase();

        String extension = validator.validateGuitarProFile(
                new MockMultipartFile("file", filename, null, contentOf(expected)));

        assertThat(extension).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"song.gp", "song.gp5", "song.gpx"})
    void validateGuitarProFile_ContentNotOfThatFormat_Rejects(String filename) {
        MockMultipartFile file = new MockMultipartFile("file", filename, null, "%PDF-1.7".getBytes(US_ASCII));

        assertThatThrownBy(() -> validator.validateGuitarProFile(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("The file is not a valid .gp");
    }

    @Test
    void validateGuitarProFile_UnreadableFile_FailsAsAServerError() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(10L);
        when(file.getOriginalFilename()).thenReturn("song.gp5");
        when(file.getBytes()).thenThrow(new IOException("disk"));

        assertThatThrownBy(() -> validator.validateGuitarProFile(file))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessage("The uploaded file could not be read");
    }

    /**
     * The smallest content each format's signature check accepts.
     */
    private static byte[] contentOf(String extension) {
        return switch (extension) {
            case "gp5" -> {
                byte[] version = "FICHIER GUITAR PRO v5.10".getBytes(US_ASCII);
                byte[] content = new byte[version.length + 1];
                content[0] = (byte) version.length;
                System.arraycopy(version, 0, content, 1, version.length);
                yield content;
            }
            case "gpx" -> "BCFZ".getBytes(US_ASCII);
            default -> "PK\u0003\u0004 Content/score.gpif".getBytes(US_ASCII);
        };
    }

    @ParameterizedTest
    @ValueSource(strings = {"song.pdf", "song", "song.gp5.exe"})
    void validateGuitarProFile_OtherExtension_Rejects(String filename) {
        MockMultipartFile file = new MockMultipartFile("file", filename, null, new byte[]{1});

        assertThatThrownBy(() -> validator.validateGuitarProFile(file)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateGuitarProFile_MissingEmptyOrTooLarge_Rejects() {
        MockMultipartFile empty = new MockMultipartFile("file", "song.gp5", null, new byte[0]);
        MockMultipartFile tooLarge = new MockMultipartFile("file", "song.gp5", null,
                new byte[(int) TabValidator.MAX_FILE_SIZE_BYTES + 1]);

        assertThatThrownBy(() -> validator.validateGuitarProFile(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validateGuitarProFile(empty)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validateGuitarProFile(tooLarge))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A Guitar Pro file can be at most 10 MB");
    }
}
