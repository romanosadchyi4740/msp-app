package com.romanosadchyi.smp.music_app.rooms.internal.repository;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {
    Optional<Room> findByInviteCode(String inviteCode);

    /**
     * The room with its row locked ({@code SELECT ... FOR UPDATE}) until the transaction ends, so concurrent joins
     * count the participants one after another.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.inviteCode = :inviteCode")
    Optional<Room> findByInviteCodeForUpdate(@Param("inviteCode") String inviteCode);

    /**
     * The room with its row locked until the transaction ends, so starting, ending and closing a lesson in one room
     * happen one after another.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByInviteCode(String inviteCode);

    List<Room> findAllByHostId(UUID hostId);
}
