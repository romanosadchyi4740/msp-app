package com.romanosadchyi.smp.music_app.shared.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Stores a text column encrypted with {@link EncryptionUtil} (KAN-21), e.g. chat and room message content; use it with
 * {@code @Convert(converter = EncryptedTextConverter.class)}. Hibernate creates it through Spring, which injects the
 * cipher.
 */
@Converter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EncryptedTextConverter implements AttributeConverter<String, String> {

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
