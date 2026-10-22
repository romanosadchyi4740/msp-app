package com.romanosadchyi.smp.music_app.shared.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Encrypts a text column with {@link EncryptionUtil}: {@code @Convert(converter = EncryptedStringConverter.class)}.
 * Hibernate creates it through Spring, which injects the key.
 */
@Converter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    EncryptionUtil encryptionUtil;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return encryptionUtil.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return encryptionUtil.decrypt(dbData);
    }
}
