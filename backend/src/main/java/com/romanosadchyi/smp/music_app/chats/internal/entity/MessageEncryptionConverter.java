package com.romanosadchyi.smp.music_app.chats.internal.entity;

import com.romanosadchyi.smp.music_app.shared.util.EncryptionUtil;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Stores message content encrypted (KAN-21). Hibernate creates it through Spring, which injects the cipher.
 */
@Converter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MessageEncryptionConverter implements AttributeConverter<String, String> {

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
