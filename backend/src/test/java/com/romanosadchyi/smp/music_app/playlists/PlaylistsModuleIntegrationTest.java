package com.romanosadchyi.smp.music_app.playlists;

import com.jayway.jsonpath.JsonPath;
import com.romanosadchyi.smp.music_app.IntegrationTest;
import com.romanosadchyi.smp.music_app.music.internal.entity.Album;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.music.internal.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.music.internal.repository.TrackRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Playlists against a real database: track ids in {@code playlist_tracks} and {@code integration_request_tracks},
 * the fork and integration flow, and what deleting accounts and tracks does to other users' playlists.
 */
@IntegrationTest
// Tracks are seeded through the music repositories: creating them through the API needs audio file uploads
class PlaylistsModuleIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AlbumRepository albumRepository;

    @Autowired
    TrackRepository trackRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void playlist_KeepsTrackOrderAndShowsTrackData() throws Exception {
        Registered owner = register("Owner");
        Track first = saveTrack(owner.id(), saveAlbum(owner.id()));
        Track second = saveTrack(owner.id(), saveAlbum(owner.id()));
        UUID playlistId = createPlaylist(owner, second, first, second);

        mockMvc.perform(get("/api/playlists/" + playlistId).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracksCount").value(3))
                .andExpect(jsonPath("$.tracks[0].id").value(second.getId().toString()))
                .andExpect(jsonPath("$.tracks[1].id").value(first.getId().toString()))
                .andExpect(jsonPath("$.tracks[2].id").value(second.getId().toString()))
                .andExpect(jsonPath("$.tracks[0].authorName").value("Owner"))
                .andExpect(jsonPath("$.tracks[0].albumTitle").value("Album"));

        mockMvc.perform(delete("/api/playlists/" + playlistId + "/tracks/" + second.getId())
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks.length()").value(2))
                .andExpect(jsonPath("$.tracks[0].id").value(first.getId().toString()))
                .andExpect(jsonPath("$.tracks[1].id").value(second.getId().toString()));
        assertThat(jdbcTemplate.queryForList(
                "SELECT track_order FROM playlist_tracks WHERE playlist_id = ? ORDER BY track_order", Integer.class,
                playlistId)).containsExactly(0, 1);
    }

    @Test
    void forkProposeAccept_AddsTheForksNewTracksToTheOrigin() throws Exception {
        Registered owner = register("Owner");
        Registered forker = register("Forker");
        Track original = saveTrack(owner.id(), saveAlbum(owner.id()));
        Track added = saveTrack(forker.id(), saveAlbum(forker.id()));
        UUID originId = createPlaylist(owner, original);

        UUID forkId = fork(forker, originId);
        addTrack(forker, forkId, added);
        String request = mockMvc.perform(post("/api/playlists/" + forkId + "/propose-integration")
                        .header(HttpHeaders.AUTHORIZATION, forker.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requester.id").value(forker.id().toString()))
                .andExpect(jsonPath("$.proposedTracks.length()").value(1))
                .andExpect(jsonPath("$.proposedTracks[0].id").value(added.getId().toString()))
                .andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(request, "$.id");

        mockMvc.perform(get("/api/playlists/" + originId + "/integrations").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].proposedTracks[0].id").value(added.getId().toString()));
        mockMvc.perform(post("/api/integrations/" + requestId + "/resolve").param("action", "ACCEPT")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(get("/api/playlists/" + originId).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks[0].id").value(original.getId().toString()))
                .andExpect(jsonPath("$.tracks[1].id").value(added.getId().toString()));
        mockMvc.perform(get("/api/playlists/" + forkId).header(HttpHeaders.AUTHORIZATION, forker.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forkedFromId").value(originId.toString()))
                .andExpect(jsonPath("$.forkedFromOwnerName").value("Owner"));
    }

    @Test
    void deleteAccount_OfTheForker_DeletesTheirForksAndRequestsOnly() throws Exception {
        Registered owner = register("Owner");
        Registered forker = register("Forker");
        Track original = saveTrack(owner.id(), saveAlbum(owner.id()));
        Track added = saveTrack(owner.id(), saveAlbum(owner.id()));
        UUID originId = createPlaylist(owner, original);
        UUID forkId = fork(forker, originId);
        addTrack(forker, forkId, added);
        propose(forker, forkId);

        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, forker.bearer()))
                .andExpect(status().isNoContent());

        assertThat(countRows("playlists WHERE owner_id = ?", forker.id())).isZero();
        assertThat(countRows("integration_requests WHERE requester_id = ?", forker.id())).isZero();
        mockMvc.perform(get("/api/playlists/" + originId).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks.length()").value(1));
    }

    @Test
    void deleteAccount_OfAForkerWhoProposedTheirOwnTrack_DeletesTheRequest() throws Exception {
        Registered owner = register("Owner");
        Registered forker = register("Forker");
        Track ownTrack = saveTrack(forker.id(), saveAlbum(forker.id()));
        UUID originId = createPlaylist(owner);
        UUID forkId = fork(forker, originId);
        addTrack(forker, forkId, ownTrack);
        propose(forker, forkId);

        // Music deletes the forker's track and playlists deletes their request, in one transaction and either order
        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, forker.bearer()))
                .andExpect(status().isNoContent());

        assertThat(countRows("integration_requests WHERE requester_id = ?", forker.id())).isZero();
        assertThat(countRows("integration_request_tracks WHERE track_id = ?", ownTrack.getId())).isZero();
        mockMvc.perform(get("/api/playlists/" + originId + "/integrations").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deleteAccount_OfTheOriginOwner_KeepsOtherUsersForks() throws Exception {
        Registered owner = register("Owner");
        Registered forker = register("Forker");
        Track forkersTrack = saveTrack(forker.id(), saveAlbum(forker.id()));
        UUID originId = createPlaylist(owner);
        UUID forkId = fork(forker, originId);
        addTrack(forker, forkId, forkersTrack);
        propose(forker, forkId);

        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isNoContent());

        assertThat(countRows("playlists WHERE id = ?", originId)).isZero();
        assertThat(countRows("integration_requests WHERE requester_id = ?", forker.id())).isZero();
        mockMvc.perform(get("/api/playlists/" + forkId).header(HttpHeaders.AUTHORIZATION, forker.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forkedFromId").value(nullValue()))
                .andExpect(jsonPath("$.tracks[0].id").value(forkersTrack.getId().toString()));
    }

    @Test
    void deleteAlbum_TrackProposedInARequest_RemovesItFromTheRequest() throws Exception {
        Registered owner = register("Owner");
        Registered forker = register("Forker");
        Album deletedAlbum = saveAlbum(forker.id());
        Track deleted = saveTrack(forker.id(), deletedAlbum);
        Track kept = saveTrack(forker.id(), saveAlbum(forker.id()));
        UUID originId = createPlaylist(owner);
        UUID forkId = fork(forker, originId);
        addTrack(forker, forkId, deleted);
        addTrack(forker, forkId, kept);
        propose(forker, forkId);

        mockMvc.perform(delete("/api/albums/" + deletedAlbum.getId()).header(HttpHeaders.AUTHORIZATION, forker.bearer()))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get("/api/playlists/" + originId + "/integrations").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].proposedTracks.length()").value(1))
                .andExpect(jsonPath("$[0].proposedTracks[0].id").value(kept.getId().toString()));
        mockMvc.perform(get("/api/playlists/" + forkId).header(HttpHeaders.AUTHORIZATION, forker.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks.length()").value(1))
                .andExpect(jsonPath("$.tracks[0].id").value(kept.getId().toString()));
    }

    @Test
    void deleteAlbum_OnlyTrackProposedInARequest_RejectsTheRequest() throws Exception {
        Registered owner = register("Owner");
        Registered forker = register("Forker");
        Album deletedAlbum = saveAlbum(forker.id());
        Track deleted = saveTrack(forker.id(), deletedAlbum);
        UUID originId = createPlaylist(owner);
        UUID forkId = fork(forker, originId);
        addTrack(forker, forkId, deleted);
        propose(forker, forkId);

        mockMvc.perform(delete("/api/albums/" + deletedAlbum.getId()).header(HttpHeaders.AUTHORIZATION, forker.bearer()))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get("/api/playlists/" + originId + "/integrations").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(jdbcTemplate.queryForList("SELECT status FROM integration_requests WHERE requester_id = ?",
                String.class, forker.id())).containsExactly("REJECTED");
    }

    @Test
    void userPublicPlaylists_ListsEachPlaylistWithItsTracksInOrder() throws Exception {
        Registered owner = register("Owner");
        Track first = saveTrack(owner.id(), saveAlbum(owner.id()));
        Track second = saveTrack(owner.id(), saveAlbum(owner.id()));
        createPlaylist(owner, first);
        UUID repeated = createPlaylist(owner, second, first, second);
        createPlaylist(owner);

        mockMvc.perform(get("/api/playlists/user/" + owner.id()).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].tracksCount").value(containsInAnyOrder(0, 1, 3)))
                .andExpect(jsonPath("$[?(@.id == '%s')].tracks[*].id".formatted(repeated)).value(contains(
                        second.getId().toString(), first.getId().toString(), second.getId().toString())));
    }

    @Test
    void myPlaylists_MoreForks_TakeNoMoreStatements() throws Exception {
        Registered owner = register("Owner");
        Registered forker = register("Forker");
        Track track = saveTrack(owner.id(), saveAlbum(owner.id()));
        String myPlaylists = "/api/playlists/me";
        String publicPlaylists = "/api/playlists/user/" + forker.id();
        publicFork(forker, createPlaylist(owner, track));
        long oneFork = statementsToList(myPlaylists, forker, 1);
        long onePublicFork = statementsToList(publicPlaylists, forker, 1);

        publicFork(forker, createPlaylist(owner, track));
        publicFork(forker, createPlaylist(owner, track));

        assertThat(statementsToList(myPlaylists, forker, 3)).isEqualTo(oneFork);
        assertThat(statementsToList(publicPlaylists, forker, 3)).isEqualTo(onePublicFork);
    }

    /**
     * The SQL statements a playlist list endpoint prepares, after checking it lists the expected forks.
     */
    private long statementsToList(String url, Registered user, int forks) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(forks))
                .andExpect(jsonPath("$[*].forkedFromOwnerName").value(everyItem(equalTo("Owner"))))
                .andExpect(jsonPath("$[*].tracksCount").value(everyItem(equalTo(1))));
        return statistics.getPrepareStatementCount();
    }

    private UUID createPlaylist(Registered owner, Track... tracks) throws Exception {
        String response = mockMvc.perform(post("/api/playlists").header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Mix\", \"isPublic\": true}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID playlistId = UUID.fromString(JsonPath.read(response, "$.id"));
        for (Track track : tracks) {
            addTrack(owner, playlistId, track);
        }
        return playlistId;
    }

    private void addTrack(Registered owner, UUID playlistId, Track track) throws Exception {
        mockMvc.perform(post("/api/playlists/" + playlistId + "/tracks/" + track.getId())
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk());
    }

    private UUID fork(Registered user, UUID playlistId) throws Exception {
        String response = mockMvc.perform(post("/api/playlists/" + playlistId + "/fork")
                        .header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    /**
     * Forks are private when created; this makes the fork public so the public list shows it too.
     */
    private void publicFork(Registered user, UUID playlistId) throws Exception {
        UUID forkId = fork(user, playlistId);
        mockMvc.perform(put("/api/playlists/" + forkId + "/visibility").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPublic").value(true));
    }

    private void propose(Registered user, UUID forkId) throws Exception {
        mockMvc.perform(post("/api/playlists/" + forkId + "/propose-integration")
                        .header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk());
    }

    private int countRows(String tableAndCondition, UUID id) {
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
