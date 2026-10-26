package com.romanosadchyi.smp.music_app.tabs.internal.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @ParameterizedTest
    @ValueSource(strings = {"4", "4/3", "0/4", "4/4/4", "four/four", "4/ 4"})
    void validateMetadata_MalformedTimeSignature_Rejects(String timeSignature) {
        assertThatThrownBy(() -> validator.validateMetadata("Song", null, null, timeSignature))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateRawText_Blank_Rejects() {
        assertThatThrownBy(() -> validator.validateRawText(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validateRawText("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"song.gp", "song.GP5", "song.gpx"})
    void validateGuitarProFile_SupportedExtension_ReturnsItInLowerCase(String filename) {
        String extension = validator.validateGuitarProFile(new MockMultipartFile("file", filename, null, new byte[]{1}));

        assertThat(extension).isEqualTo(filename.substring(filename.indexOf('.') + 1).toLowerCase());
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
