package com.romanosadchyi.smp.music_app.music;

import com.jayway.jsonpath.JsonPath;
import com.romanosadchyi.smp.music_app.IntegrationTest;
import com.romanosadchyi.smp.music_app.model.entity.Playlist;
import com.romanosadchyi.smp.music_app.music.internal.entity.Album;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.music.internal.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.music.internal.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.repository.PlaylistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deleting music against a real database: tracks that other users put in their playlists must not block the
 * deletion, and those playlists keep their remaining tracks in order.
 */
@IntegrationTest
class MusicModuleIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AlbumRepository albumRepository;

    @Autowired
    TrackRepository trackRepository;

    @Autowired
    PlaylistRepository playlistRepository;

    @Test
    void deleteAlbum_TrackInAnotherUsersPlaylist_RemovesItFromThePlaylist() throws Exception {
        Registered artist = register();
        Registered listener = register();
        Album album = saveAlbum(artist.id());
        Track deleted = saveTrack(artist.id(), album);
        Track other1 = saveTrack(listener.id(), saveAlbum(listener.id()));
        Track other2 = saveTrack(listener.id(), saveAlbum(listener.id()));
        Playlist playlist = savePlaylist(listener.id(), other1, deleted, other2);

        mockMvc.perform(delete("/api/albums/" + album.getId()).header(HttpHeaders.AUTHORIZATION, artist.bearer()))
                .andExpect(status().is2xxSuccessful());

        assertThat(trackRepository.findById(deleted.getId())).isEmpty();
        mockMvc.perform(get("/api/playlists/" + playlist.getId()).header(HttpHeaders.AUTHORIZATION, listener.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks.length()").value(2))
                .andExpect(jsonPath("$.tracks[0].id").value(other1.getId().toString()))
                .andExpect(jsonPath("$.tracks[1].id").value(other2.getId().toString()));
    }

    @Test
    void deleteAccount_TrackInAnotherUsersPlaylist_DeletesTheTrack() throws Exception {
        Registered artist = register();
        Registered listener = register();
        Track track = saveTrack(artist.id(), saveAlbum(artist.id()));
        Track kept = saveTrack(listener.id(), saveAlbum(listener.id()));
        Playlist playlist = savePlaylist(listener.id(), track, kept);

        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, artist.bearer()))
                .andExpect(status().isNoContent());

        assertThat(trackRepository.findById(track.getId())).isEmpty();
        mockMvc.perform(get("/api/playlists/" + playlist.getId()).header(HttpHeaders.AUTHORIZATION, listener.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks.length()").value(1))
                .andExpect(jsonPath("$.tracks[0].id").value(kept.getId().toString()));
    }

    @Test
    void trackList_HasAuthorNames() throws Exception {
        Registered artist = register();
        Track track = saveTrack(artist.id(), saveAlbum(artist.id()));

        mockMvc.perform(get("/api/users/" + artist.id() + "/tracks").header(HttpHeaders.AUTHORIZATION, artist.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(track.getId().toString()))
                .andExpect(jsonPath("$[0].authorName").value("Tester"));
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

    private Playlist savePlaylist(UUID ownerId, Track... tracks) {
        return playlistRepository.save(Playlist.builder()
                .title("Mix")
                .ownerId(ownerId)
                .isPublic(true)
                .tracks(new ArrayList<>(List.of(tracks)))
                .build());
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
