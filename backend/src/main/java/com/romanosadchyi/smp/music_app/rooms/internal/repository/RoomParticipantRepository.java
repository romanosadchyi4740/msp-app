package com.romanosadchyi.smp.music_app.rooms.internal.repository;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomParticipantRepository extends JpaRepository<RoomParticipant, UUID> {
    List<RoomParticipant> findAllByRoomIdOrderByJoinedAtAsc(UUID roomId);

    Optional<RoomParticipant> findByRoomIdAndUserId(UUID roomId, UUID userId);

    long countByRoomId(UUID roomId);

    List<RoomParticipant> findAllByUserId(UUID userId);

    /**
     * A user's memberships with their rooms, most recently joined first.
     */
    @Query("""
            SELECT p FROM RoomParticipant p JOIN FETCH p.room
            WHERE p.userId = :userId
            ORDER BY p.joinedAt DESC
            """)
    List<RoomParticipant> findAllWithRoomByUserId(@Param("userId") UUID userId);

    Optional<RoomParticipant> findByRoomIdAndRole(UUID roomId, RoomRole role);

    /**
     * The participant other than {@code userId} who joined earliest among those online.
     */
    Optional<RoomParticipant> findFirstByRoomIdAndUserIdNotAndOnlineTrueOrderByJoinedAtAsc(UUID roomId, UUID userId);

    /**
     * The participant other than {@code userId} who joined earliest.
     */
    Optional<RoomParticipant> findFirstByRoomIdAndUserIdNotOrderByJoinedAtAsc(UUID roomId, UUID userId);

    /**
     * Open rooms whose host went offline before {@code cutoff} while someone else is online to take over.
     */
    @Query("""
            SELECT h.room.id FROM RoomParticipant h
            WHERE h.role = :hostRole AND h.online = false AND h.offlineSince <= :cutoff AND h.room.status = :open
              AND EXISTS (SELECT o FROM RoomParticipant o WHERE o.room = h.room AND o.online = true)
            """)
    List<UUID> findRoomsWithHostOfflineSince(@Param("cutoff") Instant cutoff, @Param("hostRole") RoomRole hostRole,
                                             @Param("open") RoomStatus open);

    /**
     * After a restart no session is open, so nobody is online; everyone who was counts as offline from {@code now}.
     */
    @Modifying
    @Query("UPDATE RoomParticipant p SET p.online = false, p.offlineSince = :now WHERE p.online = true")
    int markAllOffline(@Param("now") Instant now);

    @Query("""
            SELECT p.room.id AS roomId, COUNT(p) AS count FROM RoomParticipant p
            WHERE p.room.id IN :roomIds
            GROUP BY p.room.id
            """)
    List<RoomParticipantCount> countByRoomIds(@Param("roomIds") Collection<UUID> roomIds);
}
