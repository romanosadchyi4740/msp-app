package com.romanosadchyi.smp.music_app.search;

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
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Global search against a real database, with the response shape {@code SearchPage.jsx} reads
 * ({@code users.content}, {@code albums.content}, {@code tracks.content}). Music is seeded through the music
 * repositories because creating tracks through the API needs audio file uploads.
 */
@IntegrationTest
class SearchModuleIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AlbumRepository albumRepository;

    @Autowired
    TrackRepository trackRepository;

    @Test
    void search_FindsUsersAlbumsAndTracksByName() throws Exception {
        String name = "Zq" + UUID.randomUUID().toString().substring(0, 8);
        Registered artist = register(name + " Artist");
        Album album = albumRepository.save(Album.builder()
                .title(name + " Album")
                .authorId(artist.id())
                .releaseDate(LocalDate.now())
                .build());
        Track track = trackRepository.save(Track.builder()
                .title(name + " Track")
                .duration(180)
                .fileKey("tracks/" + UUID.randomUUID() + ".mp3")
                .authorId(artist.id())
                .album(album)
                .build());

        mockMvc.perform(get("/api/search").param("q", name).header(HttpHeaders.AUTHORIZATION, artist.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.content.length()").value(1))
                .andExpect(jsonPath("$.users.content[0].id").value(artist.id().toString()))
                .andExpect(jsonPath("$.albums.content.length()").value(1))
                .andExpect(jsonPath("$.albums.content[0].id").value(album.getId().toString()))
                .andExpect(jsonPath("$.tracks.content.length()").value(1))
                .andExpect(jsonPath("$.tracks.content[0].id").value(track.getId().toString()))
                .andExpect(jsonPath("$.tracks.content[0].authorName").value(name + " Artist"));
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
