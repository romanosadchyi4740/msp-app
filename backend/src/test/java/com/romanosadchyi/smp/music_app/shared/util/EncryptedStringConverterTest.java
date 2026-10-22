package com.romanosadchyi.smp.music_app.shared.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncryptedStringConverterTest {

    @Mock
    EncryptionUtil encryptionUtil;

    @InjectMocks
    EncryptedStringConverter converter;

    @Test
    void convertToDatabaseColumn_Encrypts() {
        when(encryptionUtil.encrypt("hello")).thenReturn("v2:cipher");

        assertThat(converter.convertToDatabaseColumn("hello")).isEqualTo("v2:cipher");
    }

    @Test
    void convertToEntityAttribute_Decrypts() {
        when(encryptionUtil.decrypt("v2:cipher")).thenReturn("hello");

        assertThat(converter.convertToEntityAttribute("v2:cipher")).isEqualTo("hello");
    }
}
