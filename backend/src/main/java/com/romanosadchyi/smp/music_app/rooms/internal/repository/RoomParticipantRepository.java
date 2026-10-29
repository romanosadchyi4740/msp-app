package com.romanosadchyi.smp.music_app.rooms.internal.repository;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    @Query("""
            SELECT p.room.id AS roomId, COUNT(p) AS count FROM RoomParticipant p
            WHERE p.room.id IN :roomIds
            GROUP BY p.room.id
            """)
    List<RoomParticipantCount> countByRoomIds(@Param("roomIds") Collection<UUID> roomIds);
}
