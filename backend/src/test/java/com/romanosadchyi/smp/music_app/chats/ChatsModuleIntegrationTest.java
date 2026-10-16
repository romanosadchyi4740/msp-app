package com.romanosadchyi.smp.music_app.chats;

import com.jayway.jsonpath.JsonPath;
import com.romanosadchyi.smp.music_app.IntegrationTest;
import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMessageRequestDto;
import com.romanosadchyi.smp.music_app.chats.internal.service.ChatMessageService;
import com.romanosadchyi.smp.music_app.music.internal.entity.Album;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.music.internal.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.music.internal.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chats against a real database: shared tracks stored as ids, the message history and chat list built from the
 * music and users modules, and what deleting accounts and tracks does to other users' chats. Messages arrive over
 * STOMP in production, so the tests send them through {@link ChatMessageService}; tracks are seeded through the
 * music repositories because creating them through the API needs audio file uploads.
 */
@IntegrationTest
class ChatsModuleIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ChatMessageService chatMessageService;

    @Autowired
    AlbumRepository albumRepository;

    @Autowired
    TrackRepository trackRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void messageHistoryAndChatList_ShowTheSharedTrack() throws Exception {
        Registered alice = register("Alice");
        Registered bob = register("Bob");
        Track track = saveTrack(alice.id(), saveAlbum(alice.id()));
        UUID chatId = openChat(alice, bob);

        send(alice, chatId, "first", null);
        send(alice, chatId, "listen", track.getId());

        mockMvc.perform(get("/api/chats/" + chatId + "/messages").header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].content").value("listen"))
                .andExpect(jsonPath("$.content[0].sender.id").value(alice.id().toString()))
                .andExpect(jsonPath("$.content[0].sharedTrack.id").value(track.getId().toString()))
                .andExpect(jsonPath("$.content[0].sharedTrack.authorName").value("Alice"))
                .andExpect(jsonPath("$.content[0].sharedTrackTimestamp").value(42))
                .andExpect(jsonPath("$.content[1].sharedTrack").value(nullValue()));
        mockMvc.perform(get("/api/chats").header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(chatId.toString()))
                .andExpect(jsonPath("$[0].lastMessage.content").value("listen"))
                .andExpect(jsonPath("$[0].lastMessage.sharedTrack.id").value(track.getId().toString()));
        assertThat(jdbcTemplate.queryForObject("SELECT content FROM messages WHERE chat_id = ? AND shared_track_id = ?",
                String.class, chatId, track.getId())).isNotEqualTo("listen");
    }

    @Test
    void deleteAlbum_TrackSharedInAChat_KeepsTheMessageWithoutTheTrack() throws Exception {
        Registered alice = register("Alice");
        Registered bob = register("Bob");
        Album album = saveAlbum(alice.id());
        Track track = saveTrack(alice.id(), album);
        UUID chatId = openChat(bob, alice);
        send(bob, chatId, "this one", track.getId());

        mockMvc.perform(delete("/api/albums/" + album.getId()).header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get("/api/chats/" + chatId + "/messages").header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].content").value("this one"))
                .andExpect(jsonPath("$.content[0].sharedTrack").value(nullValue()));
    }

    @Test
    void deleteAccount_DirectChat_KeepsTheMessagesUnderTheAnonymizedUser() throws Exception {
        Registered alice = register("Alice");
        Registered bob = register("Bob");
        UUID chatId = openChat(alice, bob);
        send(bob, chatId, "from bob", null);
        send(alice, chatId, "from alice", null);

        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/chats/" + chatId + "/messages").header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].content").value("from alice"))
                .andExpect(jsonPath("$.content[0].sender.id").value(alice.id().toString()))
                .andExpect(jsonPath("$.content[0].sender.displayName").value("Disconnected Node"));
        mockMvc.perform(get("/api/chats").header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].participants.length()").value(2))
                .andExpect(jsonPath("$[0].participants[?(@.id == '%s')].displayName".formatted(alice.id()))
                        .value("Disconnected Node"));
        mockMvc.perform(post("/api/chats/with/" + alice.id()).header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(chatId.toString()));
    }

    @Test
    void deleteAccount_TokenStillValid_CannotOpenChatsOrBeInvited() throws Exception {
        Registered alice = register("Alice");
        Registered bob = register("Bob");
        UUID chatId = openChat(alice, bob);

        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isNoContent());

        Registered carol = register("Carol");
        mockMvc.perform(post("/api/chats/with/" + carol.id()).header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/chats/cluster").header(HttpHeaders.AUTHORIZATION, carol.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupName\": \"Band\", \"participantIds\": [\"%s\"]}".formatted(alice.id())))
                .andExpect(status().isNotFound());
        assertThatThrownBy(() -> send(alice, chatId, "still here?", null))
                .isInstanceOf(UserNotFoundException.class);
        mockMvc.perform(get("/api/users/search").param("q", "Disconnected")
                        .header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(alice.id())).isEmpty());
    }

    @Test
    void deleteAccount_GroupChat_RemovesTheMemberButKeepsTheirMessages() throws Exception {
        Registered alice = register("Alice");
        Registered bob = register("Bob");
        Registered carol = register("Carol");
        UUID groupId = createGroup(alice, bob, carol);
        send(bob, groupId, "hi all", null);

        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isNoContent());

        assertThat(count("chat_memberships WHERE user_id = ?", bob.id())).isZero();
        mockMvc.perform(get("/api/chats/" + groupId + "/messages").header(HttpHeaders.AUTHORIZATION, carol.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].sender.displayName").value("Disconnected Node"));
    }

    @Test
    void deleteAccount_BothMembersOfADirectChat_DeletesTheChat() throws Exception {
        Registered alice = register("Alice");
        Registered bob = register("Bob");
        UUID chatId = openChat(alice, bob);
        send(alice, chatId, "from alice", null);
        send(bob, chatId, "from bob", null);

        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isNoContent());
        assertThat(count("chats WHERE id = ?", chatId)).isOne();

        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isNoContent());
        assertThat(count("chats WHERE id = ?", chatId)).isZero();
        assertThat(count("messages WHERE chat_id = ?", chatId)).isZero();
        assertThat(count("chat_memberships WHERE chat_id = ?", chatId)).isZero();
    }

    @Test
    void deleteAccount_LastMemberOfAGroup_DeletesTheGroup() throws Exception {
        Registered alice = register("Alice");
        Registered bob = register("Bob");
        UUID groupId = createGroup(alice, bob);
        send(alice, groupId, "welcome", null);

        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isNoContent());
        assertThat(count("chats WHERE id = ?", groupId)).isOne();

        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isNoContent());
        assertThat(count("chats WHERE id = ?", groupId)).isZero();
        assertThat(count("messages WHERE chat_id = ?", groupId)).isZero();
    }

    private UUID openChat(Registered initiator, Registered recipient) throws Exception {
        String response = mockMvc.perform(post("/api/chats/with/" + recipient.id())
                        .header(HttpHeaders.AUTHORIZATION, initiator.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private UUID createGroup(Registered creator, Registered... members) throws Exception {
        String memberIds = Arrays.stream(members)
                .map(member -> "\"" + member.id() + "\"")
                .collect(Collectors.joining(", "));
        String response = mockMvc.perform(post("/api/chats/cluster").header(HttpHeaders.AUTHORIZATION, creator.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupName\": \"Band\", \"participantIds\": [%s]}".formatted(memberIds)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private void send(Registered sender, UUID chatId, String content, UUID trackId) {
        Integer timestamp = trackId != null ? 42 : null;
        chatMessageService.processMessage(
                new ChatMessageRequestDto(chatId, null, content, trackId, timestamp, null), sender.id());
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
