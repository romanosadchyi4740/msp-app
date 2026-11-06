package com.romanosadchyi.smp.music_app.sync.internal.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TimeSignatureTest {

    @Test
    void parse_ReadsBothNumbers() {
        assertThat(TimeSignature.parse("6/8")).isEqualTo(new TimeSignature(6, 8));
        assertThat(TimeSignature.parse(" 12/16 ")).isEqualTo(new TimeSignature(12, 16));
    }

    @Test
    void parse_Null_IsFourFour() {
        assertThat(TimeSignature.parse(null)).isEqualTo(TimeSignature.COMMON);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "4", "4/3", "0/4", "x/4", "4/4/4", "100/4"})
    void parse_OtherForms_AreFourFour(String value) {
        assertThat(TimeSignature.parse(value)).isEqualTo(TimeSignature.COMMON);
    }

    @Test
    void quarterBeatsPerMeasure_CountsInQuarterNotes() {
        assertThat(new TimeSignature(4, 4).quarterBeatsPerMeasure()).isEqualTo(4.0);
        assertThat(new TimeSignature(3, 4).quarterBeatsPerMeasure()).isEqualTo(3.0);
        assertThat(new TimeSignature(6, 8).quarterBeatsPerMeasure()).isEqualTo(3.0);
        assertThat(new TimeSignature(2, 2).quarterBeatsPerMeasure()).isEqualTo(4.0);
        assertThat(new TimeSignature(7, 8).quarterBeatsPerMeasure()).isEqualTo(3.5);
    }
}
