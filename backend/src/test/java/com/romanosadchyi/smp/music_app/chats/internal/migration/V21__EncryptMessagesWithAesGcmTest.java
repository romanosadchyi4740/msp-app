package com.romanosadchyi.smp.music_app.chats.internal.migration;

import com.jayway.jsonpath.JsonPath;
import com.romanosadchyi.smp.music_app.IntegrationTest;
import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMessageRequestDto;
import com.romanosadchyi.smp.music_app.chats.internal.service.ChatMessageService;
import com.romanosadchyi.smp.music_app.shared.util.EncryptionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The KAN-21 migration against a real database: messages in the legacy AES-CBC format and messages stored before
 * encryption existed both end up in the AES-GCM format and read back unchanged. Flyway already ran it on the empty
 * database, so the test writes old-format rows and runs it again.
 */
@IntegrationTest
class V21__EncryptMessagesWithAesGcmTest {

    @Autowired
    V21__EncryptMessagesWithAesGcm migration;

    @Autowired
    ChatMessageService chatMessageService;

    @Autowired
    EncryptionProperties encryptionProperties;

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MockMvc mockMvc;

    @Test
    void reencrypt_LegacyAndPlaintextMessages_AreReadableInTheNewFormat() throws Exception {
        Registered alice = register();
        Registered bob = register();
        UUID chatId = openChat(alice, bob);
        for (int i = 0; i < 3; i++) {
            send(alice, chatId, "already v2");
        }
        List<UUID> ids = jdbcTemplate.queryForList("SELECT id FROM messages WHERE chat_id = ?", UUID.class, chatId);
        UUID legacy = ids.get(0);
        UUID plaintext = ids.get(1);
        UUID current = ids.get(2);
        jdbcTemplate.update("UPDATE messages SET content = ? WHERE id = ?", legacyEncrypt("from the CBC days"), legacy);
        jdbcTemplate.update("UPDATE messages SET content = ? WHERE id = ?", "before encryption", plaintext);
        String currentCiphertext = content(current);

        try (Connection connection = dataSource.getConnection()) {
            migration.reencrypt(connection);
        }

        assertThat(content(legacy)).startsWith("v2:");
        assertThat(content(plaintext)).startsWith("v2:");
        assertThat(content(current)).isEqualTo(currentCiphertext);
        mockMvc.perform(get("/api/chats/" + chatId + "/messages").header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].content",
                        containsInAnyOrder("already v2", "before encryption", "from the CBC days")));
    }

    @Test
    void reencrypt_CiphertextOfAnotherKey_FailsAndChangesNothing() throws Exception {
        Registered alice = register();
        UUID chatId = openChat(alice, register());
        send(alice, chatId, "placeholder");
        UUID messageId = jdbcTemplate.queryForObject("SELECT id FROM messages WHERE chat_id = ?", UUID.class, chatId);
        String otherKeyCiphertext = legacyEncrypt("written with another key", "abcdefghijabcdefghijabcdefghij12");
        jdbcTemplate.update("UPDATE messages SET content = ? WHERE id = ?", otherKeyCiphertext, messageId);

        try (Connection connection = dataSource.getConnection()) {
            // Flyway runs migrations in a transaction
            connection.setAutoCommit(false);
            assertThatThrownBy(() -> migration.reencrypt(connection))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("1 messages do not decrypt");
            connection.rollback();
        } finally {
            jdbcTemplate.update("DELETE FROM messages WHERE id = ?", messageId);
        }
    }

    private String content(UUID messageId) {
        return jdbcTemplate.queryForObject("SELECT content FROM messages WHERE id = ?", String.class, messageId);
    }

    private void send(Registered sender, UUID chatId, String content) {
        chatMessageService.processMessage(new ChatMessageRequestDto(chatId, null, content, null, null, null),
                sender.id());
    }

    private String legacyEncrypt(String plainText) throws Exception {
        return legacyEncrypt(plainText, encryptionProperties.secretKey());
    }

    private static String legacyEncrypt(String plainText, String key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
                new IvParameterSpec("encryptionIntVec".getBytes(StandardCharsets.UTF_8)));
        return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
    }

    private UUID openChat(Registered initiator, Registered recipient) throws Exception {
        String response = mockMvc.perform(post("/api/chats/with/" + recipient.id())
                        .header(HttpHeaders.AUTHORIZATION, initiator.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private Registered register() throws Exception {
        String body = """
                {"email": "%s@example.com", "password": "secret123", "displayName": "Tester"}
                """.formatted(UUID.randomUUID());
        String response = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Registered(UUID.fromString(JsonPath.read(response, "$.userId")), JsonPath.read(response, "$.accessToken"));
    }

    private record Registered(UUID id, String token) {

        String bearer() {
            return "Bearer " + token;
        }
    }
}
