package com.romanosadchyi.smp.music_app.chats.internal.migration;

import com.romanosadchyi.smp.music_app.shared.exception.DecryptionException;
import com.romanosadchyi.smp.music_app.shared.util.EncryptionUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

/**
 * Re-encrypts chat messages from the legacy AES-CBC format with a fixed IV to AES-GCM ({@code v2:}) with a random IV
 * per message (KAN-21). Content saved before encryption was introduced is plaintext and is encrypted as it is.
 * <p>
 * A row that could be legacy ciphertext but does not decrypt stops the migration, and its transaction rolls back:
 * that means {@code encryption.secret-key} is not the key the messages were written with, so it must not be changed
 * in the same deploy as this migration. Plaintext that happens to start with {@code v2:} is taken as already
 * migrated.
 * <p>
 * A Spring bean, so Flyway runs it with the key injected. Keep the class: Flyway fails validation when an applied
 * migration can no longer be resolved.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class V21__EncryptMessagesWithAesGcm extends BaseJavaMigration {

    static final int BATCH_SIZE = 500;
    static final int AES_BLOCK_SIZE = 16;

    EncryptionUtil encryptionUtil;

    @Override
    public void migrate(Context context) throws SQLException {
        reencrypt(context.getConnection());
    }

    /**
     * @return the number of re-encrypted messages
     * @throws IllegalStateException if a message looks encrypted but does not decrypt with the configured key
     */
    int reencrypt(Connection connection) throws SQLException {
        int reencrypted = 0;
        int unencrypted = 0;
        int undecryptable = 0;
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id, content FROM messages WHERE content NOT LIKE 'v2:%'");
             PreparedStatement update = connection.prepareStatement("UPDATE messages SET content = ? WHERE id = ?")) {
            // Streams the rows instead of loading the table; the Postgres driver needs autocommit off for that
            select.setFetchSize(BATCH_SIZE);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    String content = rows.getString("content");
                    String plainText;
                    try {
                        plainText = encryptionUtil.decrypt(content);
                    } catch (DecryptionException ex) {
                        if (couldBeLegacyCiphertext(content)) {
                            undecryptable++;
                            continue;
                        }
                        plainText = content;
                        unencrypted++;
                    }
                    update.setString(1, encryptionUtil.encrypt(plainText));
                    update.setObject(2, rows.getObject("id"));
                    update.addBatch();
                    if (++reencrypted % BATCH_SIZE == 0) {
                        update.executeBatch();
                    }
                }
            }
            if (undecryptable > 0) {
                throw new IllegalStateException(undecryptable + " messages do not decrypt with encryption.secret-key;"
                        + " it must be the key they were written with");
            }
            update.executeBatch();
        }
        log.info("Encrypted {} messages with AES-GCM; {} of them were stored unencrypted", reencrypted, unencrypted);
        return reencrypted;
    }

    /**
     * Legacy ciphertext is Base64 of whole AES blocks; text that is not cannot have been encrypted.
     */
    private static boolean couldBeLegacyCiphertext(String content) {
        try {
            int length = Base64.getDecoder().decode(content).length;
            return length > 0 && length % AES_BLOCK_SIZE == 0;
        } catch (IllegalArgumentException notBase64) {
            return false;
        }
    }
}
