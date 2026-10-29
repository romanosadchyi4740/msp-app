package com.romanosadchyi.smp.music_app.rooms.internal.mapping;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomListItem;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomPreview;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserRole;
import com.romanosadchyi.smp.music_app.users.external.UserSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomMappingServiceTest {

    @Mock
    UserLookup userLookup;

    @InjectMocks
    RoomMappingService mappingService;

    UUID hostId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    UserSummary host = new UserSummary(hostId, "Teacher", "teacher.png", UserRole.ROLE_USER);
    UserSummary student = new UserSummary(studentId, "Student", null, UserRole.ROLE_USER);
    Room room = Room.builder().id(UUID.randomUUID()).hostId(hostId).name("Lesson").inviteCode("Abc123Def456")
            .build();
    List<RoomParticipant> participants = List.of(
            RoomParticipant.builder().room(room).userId(hostId).role(RoomRole.HOST).build(),
            RoomParticipant.builder().room(room).userId(studentId).role(RoomRole.PARTICIPANT).online(true).build());

    @Test
    void map_ForTheHost_IncludesTheInviteCodeAndResolvesUsersInOneCall() {
        when(userLookup.findSummaries(List.of(hostId, studentId)))
                .thenReturn(Map.of(hostId, host, studentId, student));

        RoomResponse response = mappingService.map(room, participants, hostId);

        assertThat(response.getInviteCode()).isEqualTo("Abc123Def456");
        assertThat(response.getMyRole()).hasToString("HOST");
        assertThat(response.getName()).isEqualTo("Lesson");
        assertThat(response.getStatus()).isEqualTo(RoomStatus.OPEN);
        assertThat(response.getCapacity()).isEqualTo(5);
        assertThat(response.getParticipants()).hasSize(2);
        assertThat(response.getParticipants().get(0).getDisplayName()).isEqualTo("Teacher");
        assertThat(response.getParticipants().get(0).getAvatarUrl()).isEqualTo("teacher.png");
        assertThat(response.getParticipants().get(1).getDisplayName()).isEqualTo("Student");
        assertThat(response.getParticipants().get(1).getOnline()).isTrue();
        verify(userLookup, never()).getSummary(any());
        verify(userLookup, never()).findSummary(any());
    }

    @Test
    void map_ForAParticipant_HidesTheInviteCode() {
        when(userLookup.findSummaries(any())).thenReturn(Map.of());

        RoomResponse response = mappingService.map(room, participants, studentId);

        assertThat(response.getInviteCode()).isNull();
        assertThat(response.getMyRole()).hasToString("PARTICIPANT");
        assertThat(response.getParticipants().get(1).getDisplayName()).isNull();
    }

    @Test
    void mapPreview_FullRoom_IsMarkedFull() {
        when(userLookup.findSummary(hostId)).thenReturn(Optional.of(host));

        RoomPreview preview = mappingService.mapPreview(room, 5);

        assertThat(preview.getHostName()).isEqualTo("Teacher");
        assertThat(preview.getParticipantCount()).isEqualTo(5);
        assertThat(preview.getFull()).isTrue();
    }

    @Test
    void mapPreview_RoomWithAFreePlace_IsNotFull() {
        when(userLookup.findSummary(hostId)).thenReturn(Optional.empty());

        RoomPreview preview = mappingService.mapPreview(room, 4);

        assertThat(preview.getHostName()).isNull();
        assertThat(preview.getFull()).isFalse();
    }

    @Test
    void mapAll_Memberships_ResolvesHostsInOneCall() {
        Room other = Room.builder().id(UUID.randomUUID()).hostId(hostId).name("Rehearsal").inviteCode("x").build();
        List<RoomParticipant> memberships = List.of(
                RoomParticipant.builder().room(room).userId(studentId).role(RoomRole.PARTICIPANT).build(),
                RoomParticipant.builder().room(other).userId(studentId).role(RoomRole.PARTICIPANT).build());
        when(userLookup.findSummaries(List.of(hostId))).thenReturn(Map.of(hostId, host));

        List<RoomListItem> items = mappingService.mapAll(memberships, Map.of(room.getId(), 3L));

        assertThat(items).extracting(RoomListItem::getName).containsExactly("Lesson", "Rehearsal");
        assertThat(items).extracting(RoomListItem::getHostName).containsOnly("Teacher");
        assertThat(items).extracting(RoomListItem::getParticipantCount).containsExactly(3, 0);
        verify(userLookup, never()).findSummary(any());
    }
}
