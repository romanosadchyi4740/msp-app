package com.romanosadchyi.smp.music_app.repository;

import com.romanosadchyi.smp.music_app.model.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
