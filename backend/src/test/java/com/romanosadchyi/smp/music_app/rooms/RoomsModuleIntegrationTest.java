package com.romanosadchyi.smp.music_app.rooms;

import com.jayway.jsonpath.JsonPath;
import com.romanosadchyi.smp.music_app.IntegrationTest;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomFullException;
import com.romanosadchyi.smp.music_app.rooms.internal.service.RoomService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rooms against a real database: creating, the invite flow, the capacity of 5 under concurrent joins (row lock),
 * visibility to participants only, and what account deletion does to rooms.
 */
@IntegrationTest
class RoomsModuleIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RoomService roomService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void create_MakesTheCreatorHostWithAnInviteCode() throws Exception {
        Registered host = register();

        mockMvc.perform(post("/api/rooms").header(HttpHeaders.AUTHORIZATION, host.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Lesson\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Lesson"))
                .andExpect(jsonPath("$.hostId").value(host.id().toString()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.capacity").value(5))
                .andExpect(jsonPath("$.myRole").value("HOST"))
                .andExpect(jsonPath("$.inviteCode").isString())
                .andExpect(jsonPath("$.participants", hasSize(1)))
                .andExpect(jsonPath("$.participants[0].role").value("HOST"))
                .andExpect(jsonPath("$.participants[0].displayName").value("Player"));
    }

    @Test
    void create_BlankName_Is400() throws Exception {
        mockMvc.perform(post("/api/rooms").header(HttpHeaders.AUTHORIZATION, register().bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void join_SixthParticipant_Gets409RoomIsFull() throws Exception {
        Registered host = register();
        Room room = createRoom(host);
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/rooms/join/" + room.inviteCode())
                            .header(HttpHeaders.AUTHORIZATION, register().bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.myRole").value("PARTICIPANT"))
                    .andExpect(jsonPath("$.inviteCode").value(nullValue()));
        }

        mockMvc.perform(post("/api/rooms/join/" + room.inviteCode())
                        .header(HttpHeaders.AUTHORIZATION, register().bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The room is full"));
        assertThat(participantCount(room.id())).isEqualTo(5);
    }

    @Test
    void join_Again_AddsNoDuplicateEvenWhenFull() throws Exception {
        Registered host = register();
        Room room = createRoom(host);
        Registered student = register();
        join(student, room);
        for (int i = 0; i < 3; i++) {
            join(register(), room);
        }

        mockMvc.perform(post("/api/rooms/join/" + room.inviteCode()).header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants", hasSize(5)));
        mockMvc.perform(post("/api/rooms/join/" + room.inviteCode()).header(HttpHeaders.AUTHORIZATION, host.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("HOST"));
        assertThat(participantCount(room.id())).isEqualTo(5);
    }

    @Test
    void join_ConcurrentJoins_NeverExceedTheCapacity() throws Exception {
        Room room = createRoom(register());
        List<UUID> userIds = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            userIds.add(register().id());
        }

        List<Callable<Boolean>> joins = userIds.stream().<Callable<Boolean>>map(userId -> () -> {
            try {
                roomService.join(userId, room.inviteCode());
                return true;
            } catch (RoomFullException e) {
                return false;
            }
        }).toList();
        int joined = 0;
        try (ExecutorService executor = Executors.newFixedThreadPool(joins.size())) {
            for (Future<Boolean> result : executor.invokeAll(joins)) {
                joined += result.get() ? 1 : 0;
            }
        } catch (ExecutionException e) {
            throw new AssertionError("A join failed with something other than a full room", e.getCause());
        }

        assertThat(joined).isEqualTo(4);
        assertThat(participantCount(room.id())).isEqualTo(5);
    }

    @Test
    void preview_ShowsTheRoomWithoutJoining() throws Exception {
        Room room = createRoom(register());
        Registered visitor = register();

        mockMvc.perform(get("/api/rooms/invite/" + room.inviteCode()).header(HttpHeaders.AUTHORIZATION, visitor.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lesson"))
                .andExpect(jsonPath("$.hostName").value("Player"))
                .andExpect(jsonPath("$.participantCount").value(1))
                .andExpect(jsonPath("$.full").value(false));
        mockMvc.perform(get("/api/rooms/invite/unknown").header(HttpHeaders.AUTHORIZATION, visitor.bearer()))
                .andExpect(status().isNotFound());
        assertThat(participantCount(room.id())).isEqualTo(1);
    }

    @Test
    void getRoom_OnlyForParticipants() throws Exception {
        Room room = createRoom(register());
        Registered student = register();
        Registered outsider = register();
        join(student, room);

        mockMvc.perform(get("/api/rooms/" + room.id()).header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants", hasSize(2)))
                .andExpect(jsonPath("$.participants[0].role").value("HOST"))
                .andExpect(jsonPath("$.participants[1].userId").value(student.id().toString()));
        mockMvc.perform(get("/api/rooms/" + room.id()).header(HttpHeaders.AUTHORIZATION, outsider.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void regenerateInvite_HostOnly_AndTheOldCodeStopsWorking() throws Exception {
        Registered host = register();
        Room room = createRoom(host);
        Registered student = register();
        join(student, room);

        mockMvc.perform(post("/api/rooms/" + room.id() + "/invite").header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(status().isForbidden());
        String response = mockMvc.perform(post("/api/rooms/" + room.id() + "/invite")
                        .header(HttpHeaders.AUTHORIZATION, host.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newCode = JsonPath.read(response, "$.inviteCode");

        assertThat(newCode).isNotEqualTo(room.inviteCode()).matches("[0-9A-Za-z]{12}");
        mockMvc.perform(post("/api/rooms/join/" + room.inviteCode()).header(HttpHeaders.AUTHORIZATION, register().bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/rooms/join/" + newCode).header(HttpHeaders.AUTHORIZATION, register().bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void join_ClosedRoom_Gets409() throws Exception {
        Room room = createRoom(register());
        jdbcTemplate.update("UPDATE rooms SET status = 'CLOSED' WHERE id = ?", room.id());

        mockMvc.perform(post("/api/rooms/join/" + room.inviteCode()).header(HttpHeaders.AUTHORIZATION, register().bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The room is closed"));
    }

    @Test
    void getMyRooms_ListsHostedAndJoinedRoomsWithCounts() throws Exception {
        Registered user = register();
        Room hosted = createRoom(user);
        Room joined = createRoom(register());
        join(user, joined);

        mockMvc.perform(get("/api/rooms/my").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(joined.id().toString()))
                .andExpect(jsonPath("$[0].myRole").value("PARTICIPANT"))
                .andExpect(jsonPath("$[0].participantCount").value(2))
                .andExpect(jsonPath("$[1].id").value(hosted.id().toString()))
                .andExpect(jsonPath("$[1].myRole").value("HOST"))
                .andExpect(jsonPath("$[1].participantCount").value(1));
    }

    @Test
    void insert_SecondHost_IsRejected() throws Exception {
        Room room = createRoom(register());
        UUID otherId = register().id();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO room_participants (id, room_id, user_id, role, joined_at)
                VALUES (?, ?, ?, 'HOST', now())
                """, UUID.randomUUID(), room.id(), otherId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteAccount_DeletesHostedRoomsAndPlacesInOthers() throws Exception {
        Registered user = register();
        Registered other = register();
        Room hosted = createRoom(user);
        join(other, hosted);
        Room othersRoom = createRoom(other);
        join(user, othersRoom);

        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rooms WHERE id = ?", Long.class, hosted.id()))
                .isZero();
        assertThat(participantCount(hosted.id())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM room_participants WHERE user_id = ?",
                Long.class, user.id())).isZero();
        assertThat(participantCount(othersRoom.id())).isEqualTo(1);
    }

    /**
     * The security filter already turns such a token away; {@code RoomService} checks again for callers that do not
     * pass through it.
     */
    @Test
    void deletedAccount_CannotCreateOrJoinWithItsUnexpiredToken() throws Exception {
        Room room = createRoom(register());
        Registered user = register();
        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/rooms/join/" + room.inviteCode()).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/rooms").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Lesson\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(participantCount(room.id())).isEqualTo(1);
    }

    private Room createRoom(Registered host) throws Exception {
        String response = mockMvc.perform(post("/api/rooms").header(HttpHeaders.AUTHORIZATION, host.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Lesson\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Room(UUID.fromString(JsonPath.read(response, "$.id")), JsonPath.read(response, "$.inviteCode"));
    }

    private void join(Registered user, Room room) throws Exception {
        mockMvc.perform(post("/api/rooms/join/" + room.inviteCode()).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk());
    }

    private long participantCount(UUID roomId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM room_participants WHERE room_id = ?", Long.class,
                roomId);
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

    private record Room(UUID id, String inviteCode) {
    }

    private record Registered(UUID id, String token) {

        String bearer() {
            return "Bearer " + token;
        }
    }
}
