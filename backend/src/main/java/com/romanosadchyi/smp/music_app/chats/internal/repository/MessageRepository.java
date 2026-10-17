package com.romanosadchyi.smp.music_app.chats.internal.repository;

import com.romanosadchyi.smp.music_app.chats.internal.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByChatIdOrderByTimestampDesc(UUID chatId, Pageable pageable);

    /**
     * The latest message of each of the given chats that has messages, in one query that reads one entry of the
     * {@code (chat_id, timestamp DESC)} index per chat; of two messages with the same timestamp the one with the
     * greater id wins, so the result is stable.
     */
    @Query(value = "SELECT m.* FROM chats c CROSS JOIN LATERAL (SELECT * FROM messages l WHERE l.chat_id = c.id " +
                   "ORDER BY l.timestamp DESC, l.id DESC LIMIT 1) m WHERE c.id IN (:chatIds)",
           nativeQuery = true)
    List<Message> findLatestByChatIds(@Param("chatIds") Collection<UUID> chatIds);

    @Modifying
    @Query("UPDATE Message m SET m.sharedTrackId = null WHERE m.sharedTrackId = :trackId")
    void detachTrack(@Param("trackId") UUID trackId);
}
