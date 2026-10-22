package com.romanosadchyi.smp.music_app.users;

import com.jayway.jsonpath.JsonPath;
import com.romanosadchyi.smp.music_app.IntegrationTest;
import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMessageRequestDto;
import com.romanosadchyi.smp.music_app.chats.internal.service.ChatMessageService;
import com.romanosadchyi.smp.music_app.music.internal.entity.Album;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.music.internal.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.music.internal.repository.TrackRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The users module against a real database: follows feed the telemetry feed, likes are stored by user id,
 * account deletion runs every module's cleanup, and only the user's own profile carries their email.
 */
@IntegrationTest
class UsersModuleIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AlbumRepository albumRepository;

    @Autowired
    TrackRepository trackRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ChatMessageService chatMessageService;

    @Test
    void feed_ContainsBroadcastsOfFollowedUsers() throws Exception {
        Registered reader = register();
        Registered author = register();
        mockMvc.perform(post("/api/telemetry/broadcast").header(HttpHeaders.AUTHORIZATION, author.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"hello followers\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/telemetry/feed").header(HttpHeaders.AUTHORIZATION, reader.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(post("/api/users/" + author.id() + "/follow").header(HttpHeaders.AUTHORIZATION, reader.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/telemetry/feed").header(HttpHeaders.AUTHORIZATION, reader.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("hello followers"))
                .andExpect(jsonPath("$.content[0].author.id").value(author.id().toString()));
    }

    @Test
    void likedTracks_AreStoredByUserId() throws Exception {
        Registered artist = register();
        Registered fan = register();
        Track track = saveTrack(artist.id());

        mockMvc.perform(post("/api/tracks/" + track.getId() + "/like").header(HttpHeaders.AUTHORIZATION, fan.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tracks/liked").header(HttpHeaders.AUTHORIZATION, fan.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(track.getId().toString()))
                .andExpect(jsonPath("$[0].authorId").value(artist.id().toString()));
        mockMvc.perform(get("/api/users/" + artist.id() + "/tracks").header(HttpHeaders.AUTHORIZATION, fan.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(track.getId().toString()));
    }

    @Test
    void deleteAccount_RemovesContentLikesAndFollowsAndAnonymizesUser() throws Exception {
        Registered user = becomeArtist(register());
        Registered other = register();
        Track ownTrack = saveTrack(user.id());
        Track otherTrack = saveTrack(other.id());
        mockMvc.perform(post("/api/tracks/" + otherTrack.getId() + "/like").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/users/" + other.id() + "/follow").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/users/" + user.id() + "/follow").header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isNoContent());

        assertThat(trackRepository.findById(ownTrack.getId())).isEmpty();
        assertThat(albumRepository.findAllByAuthorId(user.id())).isEmpty();
        assertThat(countRows("SELECT COUNT(*) FROM user_liked_tracks WHERE user_id = ?", user.id())).isZero();
        assertThat(countRows("SELECT COUNT(*) FROM user_followers WHERE follower_id = ? OR following_id = ?",
                user.id(), user.id())).isZero();
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followingCount").value(0))
                .andExpect(jsonPath("$.followersCount").value(0));
        mockMvc.perform(get("/api/users/following").header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT display_name, email, deleted_at FROM users WHERE id = ?", user.id());
        assertThat(row.get("display_name")).isEqualTo("Disconnected Node");
        assertThat((String) row.get("email")).startsWith("deleted_");
        assertThat(row.get("deleted_at")).isNotNull();
        mockMvc.perform(get("/api/users/" + user.id()))
                .andExpect(status().isNotFound());
    }

    @Test
    void otherUsersProfiles_HaveNoEmail_OnlyTheOwnProfileHasIt() throws Exception {
        Registered reader = register();
        Registered author = register();
        mockMvc.perform(post("/api/users/" + author.id() + "/follow").header(HttpHeaders.AUTHORIZATION, reader.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/telemetry/broadcast").header(HttpHeaders.AUTHORIZATION, author.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"hello\"}"))
                .andExpect(status().isOk());
        String chat = mockMvc.perform(post("/api/chats/with/" + reader.id())
                        .header(HttpHeaders.AUTHORIZATION, author.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..email").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        UUID chatId = UUID.fromString(JsonPath.read(chat, "$.id"));
        chatMessageService.processMessage(new ChatMessageRequestDto(chatId, null, "hi", null, null, null), author.id());

        for (String path : new String[]{"/api/chats", "/api/chats/" + chatId + "/messages", "/api/telemetry/feed",
                "/api/users/" + author.id(), "/api/users/following", "/api/users/search?q=Tester",
                "/api/search?q=Tester"}) {
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, reader.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$..displayName").isNotEmpty())
                    .andExpect(jsonPath("$..email").doesNotExist());
        }
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, reader.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(reader.email()));
    }

    private Track saveTrack(UUID authorId) {
        Album album = albumRepository.save(Album.builder()
                .title("Album")
                .authorId(authorId)
                .releaseDate(LocalDate.now())
                .build());
        return trackRepository.save(Track.builder()
                .title("Track")
                .duration(180)
                .fileKey("tracks/" + UUID.randomUUID() + ".mp3")
                .authorId(authorId)
                .album(album)
                .build());
    }

    private long countRows(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    private Registered register() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String body = """
                {"email": "%s", "password": "secret123", "displayName": "Tester"}
                """.formatted(email);
        String response = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Registered(UUID.fromString(JsonPath.read(response, "$.userId")), email,
                JsonPath.read(response, "$.accessToken"));
    }

    private Registered becomeArtist(Registered user) throws Exception {
        String authorization = mockMvc.perform(post("/api/users/me/become-artist")
                        .header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.AUTHORIZATION);
        return new Registered(user.id(), user.email(), authorization.substring("Bearer ".length()));
    }

    private record Registered(UUID id, String email, String token) {

        String bearer() {
            return "Bearer " + token;
        }
    }
}
