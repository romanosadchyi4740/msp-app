package com.romanosadchyi.smp.music_app.tabs;

import com.jayway.jsonpath.JsonPath;
import com.romanosadchyi.smp.music_app.IntegrationTest;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.AsciiTab;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.GuitarProTab;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.Tab;
import com.romanosadchyi.smp.music_app.tabs.internal.repository.TabRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The tabs table against a real database: both formats in one table, the constraints of the migration, and what
 * account deletion does to tabs.
 */
@IntegrationTest
class TabsModuleIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TabRepository tabRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void save_BothFormats_LoadsEachAsItsSubclass() throws Exception {
        UUID ownerId = register().id();
        GuitarProTab guitarProTab = tabRepository.save(guitarProTab(ownerId));
        AsciiTab asciiTab = tabRepository.save(asciiTab(ownerId));

        List<Tab> tabs = tabRepository.findAllByOwnerId(ownerId);

        assertThat(tabs).hasSize(2);
        assertThat(tabRepository.findById(guitarProTab.getId())).get()
                .isInstanceOfSatisfying(GuitarProTab.class, tab -> {
                    assertThat(tab.getFileKey()).isEqualTo("tabs/song.gp5");
                    assertThat(tab.getGpVersion()).isEqualTo("GP5");
                    assertThat(tab.getTempoBpm()).isEqualTo(120);
                    assertThat(tab.isPublic()).isFalse();
                    assertThat(tab.isHidden()).isFalse();
                    assertThat(tab.getCreatedAt()).isNotNull();
                    assertThat(tab.getUpdatedAt()).isNotNull();
                });
        assertThat(tabRepository.findById(asciiTab.getId())).get()
                .isInstanceOfSatisfying(AsciiTab.class, tab -> assertThat(tab.getRawText()).isEqualTo("e|---0---|"));
        assertThat(format(guitarProTab.getId())).isEqualTo("GUITAR_PRO");
        assertThat(format(asciiTab.getId())).isEqualTo("ASCII");
    }

    @Test
    void save_WithoutContentOrConfirmedRights_IsRejected() throws Exception {
        UUID ownerId = register().id();
        GuitarProTab withoutFile = guitarProTab(ownerId);
        withoutFile.setFileKey(null);
        AsciiTab withoutText = asciiTab(ownerId);
        withoutText.setRawText(null);
        AsciiTab rightsNotConfirmed = asciiTab(ownerId);
        rightsNotConfirmed.setRightsConfirmed(false);
        AsciiTab zeroTempo = asciiTab(ownerId);
        zeroTempo.setTempoBpm(0);

        for (Tab tab : List.of(withoutFile, withoutText, rightsNotConfirmed, zeroTempo)) {
            assertThatThrownBy(() -> tabRepository.saveAndFlush(tab)).isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(tabRepository.findAllByOwnerId(ownerId)).isEmpty();
    }

    @Test
    void insert_AsciiTabWithAFileKey_IsRejected() throws Exception {
        UUID ownerId = register().id();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO tabs (id, owner_id, format, title, raw_text, file_key, rights_confirmed, created_at, updated_at)
                VALUES (?, ?, 'ASCII', 'Riff', 'e|---0---|', 'tabs/stray.gp5', TRUE, now(), now())
                """, UUID.randomUUID(), ownerId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteAccount_DeletesOnlyThatUsersTabs() throws Exception {
        Registered user = register();
        UUID otherId = register().id();
        tabRepository.save(guitarProTab(user.id()));
        tabRepository.save(asciiTab(user.id()));
        Tab othersTab = tabRepository.save(asciiTab(otherId));

        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tabs WHERE owner_id = ?", Integer.class, user.id()))
                .isZero();
        assertThat(tabRepository.findById(othersTab.getId())).isPresent();
    }

    private String format(UUID tabId) {
        return jdbcTemplate.queryForObject("SELECT format FROM tabs WHERE id = ?", String.class, tabId);
    }

    private static GuitarProTab guitarProTab(UUID ownerId) {
        return GuitarProTab.builder()
                .ownerId(ownerId)
                .title("Song")
                .artist("Artist")
                .tempoBpm(120)
                .timeSignature("4/4")
                .rightsConfirmed(true)
                .fileKey("tabs/song.gp5")
                .gpVersion("GP5")
                .build();
    }

    private static AsciiTab asciiTab(UUID ownerId) {
        return AsciiTab.builder()
                .ownerId(ownerId)
                .title("Riff")
                .tempoBpm(90)
                .timeSignature("4/4")
                .rightsConfirmed(true)
                .rawText("e|---0---|")
                .build();
    }

    private Registered register() throws Exception {
        String body = """
                {"email": "%s@example.com", "password": "secret123", "displayName": "Player"}
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
