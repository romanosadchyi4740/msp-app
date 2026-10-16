package com.romanosadchyi.smp.music_app.chats.internal.repository;

import com.romanosadchyi.smp.music_app.chats.internal.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByChatIdOrderByTimestampDesc(UUID chatId, Pageable pageable);

    @Modifying
    @Query("UPDATE Message m SET m.sharedTrackId = null WHERE m.sharedTrackId = :trackId")
    void detachTrack(@Param("trackId") UUID trackId);

    @Modifying
    @Query("DELETE FROM Message m WHERE m.senderId = :senderId")
    void deleteAllBySenderId(@Param("senderId") UUID senderId);
}
