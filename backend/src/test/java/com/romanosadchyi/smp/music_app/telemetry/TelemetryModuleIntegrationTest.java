package com.romanosadchyi.smp.music_app.telemetry;

import com.jayway.jsonpath.JsonPath;
import com.romanosadchyi.smp.music_app.IntegrationTest;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Status broadcasts against a real database: attached tracks stored as ids and shown in the feed, and what deleting
 * accounts and tracks does to broadcasts and polls. Tracks are seeded through the music repositories because
 * creating them through the API needs audio file uploads.
 */
@IntegrationTest
class TelemetryModuleIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AlbumRepository albumRepository;

    @Autowired
    TrackRepository trackRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void feed_ShowsTheAttachedTrack() throws Exception {
        Registered author = register("Author");
        Track track = saveTrack(author.id(), saveAlbum(author.id()));
        broadcast(author, "now playing", track.getId());
        broadcast(author, "no track", null);

        mockMvc.perform(get("/api/telemetry/feed").header(HttpHeaders.AUTHORIZATION, author.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].content").value("no track"))
                .andExpect(jsonPath("$.content[0].attachedTrack").value(nullValue()))
                .andExpect(jsonPath("$.content[1].attachedTrack.id").value(track.getId().toString()))
                .andExpect(jsonPath("$.content[1].attachedTrack.authorName").value("Author"));
    }

    @Test
    void deleteAlbum_AttachedTrack_KeepsTheBroadcastWithoutIt() throws Exception {
        Registered author = register("Author");
        Album album = saveAlbum(author.id());
        Track track = saveTrack(author.id(), album);
        UUID broadcastId = broadcast(author, "now playing", track.getId());

        mockMvc.perform(delete("/api/albums/" + album.getId()).header(HttpHeaders.AUTHORIZATION, author.bearer()))
                .andExpect(status().is2xxSuccessful());

        assertThat(jdbcTemplate.queryForObject("SELECT attached_track_id FROM status_broadcasts WHERE id = ?",
                UUID.class, broadcastId)).isNull();
        mockMvc.perform(get("/api/telemetry/feed").header(HttpHeaders.AUTHORIZATION, author.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("now playing"))
                .andExpect(jsonPath("$.content[0].attachedTrack").value(nullValue()));
    }

    @Test
    void deleteAccount_DeletesTheUsersBroadcastsAndVotesOnOtherPolls() throws Exception {
        Registered author = register("Author");
        Registered voter = register("Voter");
        String response = mockMvc.perform(post("/api/telemetry/broadcast").header(HttpHeaders.AUTHORIZATION, author.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"vote\", \"poll\": {\"question\": \"Which?\", \"options\": [\"A\", \"B\"]}}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String optionId = JsonPath.read(response, "$.poll.options[0].id");
        mockMvc.perform(post("/api/telemetry/polls/options/" + optionId + "/vote").header(HttpHeaders.AUTHORIZATION, voter.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].voteCount").value(1));
        UUID votersBroadcast = broadcast(voter, "mine", null);

        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, voter.bearer()))
                .andExpect(status().isNoContent());

        assertThat(count("status_broadcasts WHERE id = ?", votersBroadcast)).isZero();
        assertThat(count("poll_votes WHERE voter_id = ?", voter.id())).isZero();
        mockMvc.perform(get("/api/telemetry/feed").header(HttpHeaders.AUTHORIZATION, author.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].poll.options[0].voteCount").value(0));
    }

    @Test
    void deleteAndVote_MissingOrNotOwn_AnswerNotFoundOrForbidden() throws Exception {
        Registered author = register("Author");
        Registered other = register("Other");
        UUID broadcastId = broadcast(author, "mine", null);

        mockMvc.perform(delete("/api/telemetry/broadcast/" + broadcastId).header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only delete your own broadcasts"));
        mockMvc.perform(delete("/api/telemetry/broadcast/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, author.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Broadcast not found"));
        mockMvc.perform(post("/api/telemetry/polls/options/" + UUID.randomUUID() + "/vote")
                        .header(HttpHeaders.AUTHORIZATION, author.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Poll option not found"));
        assertThat(count("status_broadcasts WHERE id = ?", broadcastId)).isOne();
    }

    private UUID broadcast(Registered author, String content, UUID trackId) throws Exception {
        String track = trackId != null ? "\"" + trackId + "\"" : "null";
        String response = mockMvc.perform(post("/api/telemetry/broadcast").header(HttpHeaders.AUTHORIZATION, author.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"%s\", \"attachedTrackId\": %s}".formatted(content, track)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private int count(String tableAndCondition, UUID id) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableAndCondition, Integer.class, id);
    }

    private Album saveAlbum(UUID authorId) {
        return albumRepository.save(Album.builder()
                .title("Album")
                .authorId(authorId)
                .releaseDate(LocalDate.now())
                .build());
    }

    private Track saveTrack(UUID authorId, Album album) {
        return trackRepository.save(Track.builder()
                .title("Track")
                .duration(180)
                .fileKey("tracks/" + UUID.randomUUID() + ".mp3")
                .authorId(authorId)
                .album(album)
                .build());
    }

    private Registered register(String displayName) throws Exception {
        String body = """
                {"email": "%s@example.com", "password": "secret123", "displayName": "%s"}
                """.formatted(UUID.randomUUID(), displayName);
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
