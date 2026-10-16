package com.romanosadchyi.smp.music_app;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Account deletion where the music, chats and telemetry cleanups touch the same rows in one transaction: the user's
 * own track is shared in a chat and attached to broadcasts, theirs and another user's. The cleanups run in no fixed
 * order, so whichever runs first must not break the others. Tracks are seeded through the music repositories because
 * creating them through the API needs audio file uploads; messages arrive over STOMP in production, so they are sent
 * through {@link ChatMessageService}.
 */
@IntegrationTest
class AccountDeletionIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ChatMessageService chatMessageService;

    @Autowired
    AlbumRepository albumRepository;

    @Autowired
    TrackRepository trackRepository;

    @Test
    void deleteAccount_OwnTrackSharedInChatAndAttachedToBroadcasts_LeavesOtherUsersDataReadable() throws Exception {
        Registered alice = register("Alice");
        Registered bob = register("Bob");
        Track track = trackRepository.save(Track.builder()
                .title("Track")
                .duration(180)
                .fileKey("tracks/" + UUID.randomUUID() + ".mp3")
                .authorId(alice.id())
                .album(albumRepository.save(Album.builder()
                        .title("Album")
                        .authorId(alice.id())
                        .releaseDate(LocalDate.now())
                        .build()))
                .build());
        UUID chatId = openChat(alice, bob);
        send(alice, chatId, "my track", track.getId());
        send(bob, chatId, "nice, sharing it", track.getId());
        broadcast(alice, "out now", track.getId());
        broadcast(bob, "listening", track.getId());

        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/chats/" + chatId + "/messages").header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].content").value("nice, sharing it"))
                .andExpect(jsonPath("$.content[0].sharedTrack").value(nullValue()));
        mockMvc.perform(get("/api/telemetry/feed").header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].content").value("listening"))
                .andExpect(jsonPath("$.content[0].attachedTrack").value(nullValue()));
    }

    private UUID openChat(Registered initiator, Registered recipient) throws Exception {
        String response = mockMvc.perform(post("/api/chats/with/" + recipient.id())
                        .header(HttpHeaders.AUTHORIZATION, initiator.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private void send(Registered sender, UUID chatId, String content, UUID trackId) {
        chatMessageService.processMessage(new ChatMessageRequestDto(chatId, null, content, trackId, 0, null), sender.id());
    }

    private void broadcast(Registered author, String content, UUID trackId) throws Exception {
        mockMvc.perform(post("/api/telemetry/broadcast").header(HttpHeaders.AUTHORIZATION, author.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"%s\", \"attachedTrackId\": \"%s\"}".formatted(content, trackId)))
                .andExpect(status().isOk());
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
