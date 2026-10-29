package com.romanosadchyi.smp.music_app.rooms.internal.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InviteCodeGeneratorTest {

    InviteCodeGenerator generator = new InviteCodeGenerator();

    @Test
    void generate_IsTwelveBase62Characters() {
        assertThat(generator.generate()).matches("[0-9A-Za-z]{12}");
    }

    @Test
    void generate_ManyCodes_AreAllDifferent() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codes.add(generator.generate());
        }
        assertThat(codes).hasSize(1000);
    }
}
