package com.romanosadchyi.smp.music_app.chats.internal.repository;

import com.romanosadchyi.smp.music_app.chats.internal.entity.Chat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRepository extends JpaRepository<Chat, UUID> {

    @Query("SELECT c FROM Chat c JOIN c.memberships m1 JOIN c.memberships m2 " +
           "WHERE c.isGroup = false AND m1.userId = :userId1 AND m2.userId = :userId2")
    Optional<Chat> findChatBetweenUsers(@Param("userId1") UUID userId1, @Param("userId2") UUID userId2);

    @Query("SELECT c FROM Chat c JOIN c.memberships m WHERE m.userId = :userId")
    List<Chat> findAllByParticipantId(@Param("userId") UUID userId);

    /**
     * Like {@link #findAllByParticipantId}, locking the chats in id order, so concurrent account deletions of members
     * of the same chat run one after the other and see each other's {@code deleted_at}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Chat c WHERE c.id IN (SELECT m.chat.id FROM ChatMembership m WHERE m.userId = :userId) " +
           "ORDER BY c.id")
    List<Chat> findAllByParticipantIdForUpdate(@Param("userId") UUID userId);
}
