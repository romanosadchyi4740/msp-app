package com.romanosadchyi.smp.music_app.tabs.internal.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TabTextTest {

    @Test
    void stripToNull_NullOrBlank_IsNull() {
        assertThat(TabText.stripToNull(null)).isNull();
        assertThat(TabText.stripToNull("")).isNull();
        assertThat(TabText.stripToNull(" \t\n")).isNull();
    }

    @Test
    void stripToNull_PaddedText_IsStripped() {
        assertThat(TabText.stripToNull("  Blues in A ")).isEqualTo("Blues in A");
        assertThat(TabText.stripToNull("Riff")).isEqualTo("Riff");
    }
}
