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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tabs against a real database: both formats in one table, the constraints of the migration, the REST API with its
 * ownership and visibility rules, and what account deletion does to tabs.
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

    @Test
    void uploadGuitarPro_Valid_IsStoredAndServedThroughAUrl() throws Exception {
        Registered owner = register();
        MockMultipartFile file = new MockMultipartFile("file", "song.gp5", "application/octet-stream", new byte[]{1, 2, 3});

        String response = mockMvc.perform(multipart("/api/tabs/upload").file(file)
                        .param("title", "Song")
                        .param("tempoBpm", "120")
                        .param("timeSignature", "4/4")
                        .param("isPublic", "true")
                        .param("rightsConfirmed", "true")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.format").value("GUITAR_PRO"))
                .andExpect(jsonPath("$.gpVersion").value("GP5"))
                .andExpect(jsonPath("$.isPublic").value(true))
                .andExpect(jsonPath("$.ownerName").value("Player"))
                .andExpect(jsonPath("$.fileKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        UUID tabId = UUID.fromString(JsonPath.read(response, "$.id"));

        assertThat(jdbcTemplate.queryForObject("SELECT file_key FROM tabs WHERE id = ?", String.class, tabId))
                .matches("tabs/[0-9a-f-]{36}\\.gp5");
        mockMvc.perform(get("/api/tabs/" + tabId + "/url").header(HttpHeaders.AUTHORIZATION, register().bearer()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("tabs/")));
    }

    @Test
    void uploadGuitarPro_WrongExtensionOrRightsNotConfirmed_IsBadRequest() throws Exception {
        Registered owner = register();
        MockMultipartFile pdf = new MockMultipartFile("file", "song.pdf", "application/pdf", new byte[]{1});
        MockMultipartFile gp5 = new MockMultipartFile("file", "song.gp5", "application/octet-stream", new byte[]{1});

        mockMvc.perform(multipart("/api/tabs/upload").file(pdf).param("title", "Song").param("rightsConfirmed", "true")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/tabs/upload").file(gp5).param("title", "Song")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isBadRequest());
        assertThat(tabRepository.findAllByOwnerId(owner.id())).isEmpty();
    }

    @Test
    void asciiTab_OnlyTheOwnerChangesItAndOthersSeeItOnlyWhenPublic() throws Exception {
        Registered owner = register();
        Registered other = register();
        UUID tabId = createAscii(owner, "Riff", false);
        String update = """
                {"title": "Riff 2", "tempoBpm": 100, "timeSignature": "6/8", "isPublic": true, "rawText": "B|---1---|"}
                """;

        mockMvc.perform(get("/api/tabs/" + tabId).header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/tabs/" + tabId).header(HttpHeaders.AUTHORIZATION, other.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/tabs/" + tabId).header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/tabs/" + tabId).header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Riff 2"));
        mockMvc.perform(get("/api/tabs/" + tabId).header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawText").value("B|---1---|"))
                .andExpect(jsonPath("$.timeSignature").value("6/8"));
        mockMvc.perform(get("/api/tabs/" + tabId + "/url").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/tabs/" + tabId).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/tabs/" + tabId).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createAscii_RightsNotConfirmed_IsBadRequest() throws Exception {
        Registered owner = register();

        mockMvc.perform(post("/api/tabs").header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Riff\", \"rawText\": \"e|---0---|\", \"rightsConfirmed\": false}"))
                .andExpect(status().isBadRequest());
        assertThat(tabRepository.findAllByOwnerId(owner.id())).isEmpty();
    }

    @Test
    void searchAndMyTabs_ListTheRightTabsWithoutText() throws Exception {
        Registered owner = register();
        String token = UUID.randomUUID().toString().substring(0, 8);
        createAscii(owner, "Blues " + token, true);
        createAscii(owner, token + " private", false);
        UUID hiddenId = createAscii(owner, token + " hidden", true);
        jdbcTemplate.update("UPDATE tabs SET hidden = TRUE WHERE id = ?", hiddenId);
        createAscii(owner, token + "_x", true);

        mockMvc.perform(get("/api/tabs/search").param("q", token.toUpperCase())
                        .header(HttpHeaders.AUTHORIZATION, register().bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[1].title").value("Blues " + token))
                .andExpect(jsonPath("$.content[1].rawText").value(nullValue()));
        // Wildcards in the query match only themselves
        mockMvc.perform(get("/api/tabs/search").param("q", token + "_").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value(token + "_x"));
        mockMvc.perform(get("/api/tabs/search").param("q", token + "%").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get("/api/tabs/my").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[1].title").value(token + " hidden"))
                .andExpect(jsonPath("$[1].hidden").value(true));
    }

    private UUID createAscii(Registered owner, String title, boolean isPublic) throws Exception {
        String body = """
                {"title": "%s", "rawText": "e|---0---|", "isPublic": %s, "rightsConfirmed": true}
                """.formatted(title, isPublic);
        String response = mockMvc.perform(post("/api/tabs").header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
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
