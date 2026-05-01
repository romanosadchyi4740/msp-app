package com.romanosadchyi.smp.music_app.repository;

import com.romanosadchyi.smp.music_app.model.entity.ChatMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ChatMembershipRepository extends JpaRepository<ChatMembership, UUID> {
    Optional<ChatMembership> findByChatIdAndUserId(UUID chatId, UUID userId);
}