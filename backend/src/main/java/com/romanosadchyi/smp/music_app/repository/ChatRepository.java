package com.romanosadchyi.smp.music_app.repository;

import com.romanosadchyi.smp.music_app.model.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRepository extends JpaRepository<Chat, UUID> {

    @Query("SELECT c FROM Chat c JOIN c.participants p1 JOIN c.participants p2 " +
           "WHERE p1.id = :userId1 AND p2.id = :userId2")
    Optional<Chat> findChatBetweenUsers(@Param("userId1") UUID userId1, @Param("userId2") UUID userId2);

    @Query("SELECT c FROM Chat c JOIN c.participants p WHERE p.id = :userId")
    List<Chat> findAllByParticipantId(@Param("userId") UUID userId);
}
