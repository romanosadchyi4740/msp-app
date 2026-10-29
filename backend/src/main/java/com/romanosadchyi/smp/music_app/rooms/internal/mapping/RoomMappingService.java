package com.romanosadchyi.smp.music_app.rooms.internal.mapping;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.ParticipantResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomListItem;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomPreview;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserSummary;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maps rooms to the API models generated from {@code openapi/rooms.yaml}. User names come from {@link UserLookup},
 * one call per response.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomMappingService {

    UserLookup userLookup;

    /**
     * A room as {@code viewerId}, one of {@code participants}, sees it; only the host sees the invite code.
     */
    public RoomResponse map(Room room, List<RoomParticipant> participants, UUID viewerId) {
        Map<UUID, UserSummary> users = userLookup.findSummaries(
                participants.stream().map(RoomParticipant::getUserId).toList());
        RoomRole myRole = participants.stream()
                .filter(participant -> participant.getUserId().equals(viewerId))
                .map(participant -> toRole(participant.getRole()))
                .findFirst()
                .orElse(RoomRole.PARTICIPANT);
        return new RoomResponse()
                .id(room.getId())
                .name(room.getName())
                .hostId(room.getHostId())
                .status(toStatus(room))
                .capacity(room.getCapacity())
                .inviteCode(myRole == RoomRole.HOST ? room.getInviteCode() : null)
                .myRole(myRole)
                .participants(participants.stream()
                        .map(participant -> toParticipant(participant, users.get(participant.getUserId())))
                        .toList())
                .createdAt(room.getCreatedAt());
    }

    public RoomPreview mapPreview(Room room, long participantCount) {
        UserSummary host = userLookup.findSummary(room.getHostId()).orElse(null);
        return new RoomPreview()
                .id(room.getId())
                .name(room.getName())
                .hostName(host != null ? host.displayName() : null)
                .status(toStatus(room))
                .participantCount((int) participantCount)
                .capacity(room.getCapacity())
                .full(participantCount >= room.getCapacity());
    }

    /**
     * The rooms of the given memberships, with their participant counts by room id.
     */
    public List<RoomListItem> mapAll(Collection<RoomParticipant> memberships, Map<UUID, Long> participantCounts) {
        Map<UUID, UserSummary> hosts = userLookup.findSummaries(
                memberships.stream().map(membership -> membership.getRoom().getHostId()).distinct().toList());
        return memberships.stream()
                .map(membership -> {
                    Room room = membership.getRoom();
                    UserSummary host = hosts.get(room.getHostId());
                    return new RoomListItem()
                            .id(room.getId())
                            .name(room.getName())
                            .hostId(room.getHostId())
                            .hostName(host != null ? host.displayName() : null)
                            .status(toStatus(room))
                            .myRole(toRole(membership.getRole()))
                            .participantCount(participantCounts.getOrDefault(room.getId(), 0L).intValue())
                            .capacity(room.getCapacity())
                            .createdAt(room.getCreatedAt());
                })
                .toList();
    }

    private static ParticipantResponse toParticipant(RoomParticipant participant, UserSummary user) {
        return new ParticipantResponse()
                .userId(participant.getUserId())
                .displayName(user != null ? user.displayName() : null)
                .avatarUrl(user != null ? user.avatarUrl() : null)
                .role(toRole(participant.getRole()))
                .joinedAt(participant.getJoinedAt())
                .online(participant.isOnline());
    }

    private static RoomStatus toStatus(Room room) {
        return RoomStatus.valueOf(room.getStatus().name());
    }

    /**
     * The entity's role as the API's enum of the same name and values.
     */
    private static RoomRole toRole(Enum<?> role) {
        return RoomRole.valueOf(role.name());
    }
}
