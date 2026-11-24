package com.romanosadchyi.smp.music_app.rooms.internal.repository;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface RoomMessageRepository extends JpaRepository<RoomMessage, UUID> {

    /**
     * A page of the room's messages, newest first (the id breaks ties of the same instant).
     */
    @Query("SELECT m FROM RoomMessage m WHERE m.room.id = :roomId ORDER BY m.sentAt DESC, m.id DESC")
    Page<RoomMessage> findPageByRoomId(UUID roomId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM RoomMessage m WHERE m.authorId = :authorId")
    int deleteAllByAuthorId(UUID authorId);
}
