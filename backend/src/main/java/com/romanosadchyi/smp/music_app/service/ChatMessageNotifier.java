package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.dto.response.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Chat;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Delivers a chat message to every member's {@code /user/queue/messages}, the sender included.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatMessageNotifier {

    static final String MESSAGES_QUEUE = "/queue/messages";

    SimpMessagingTemplate messagingTemplate;

    public void deliver(Chat chat, ChatMessageResponseDto message) {
        chat.getMemberships().forEach(membership ->
                messagingTemplate.convertAndSendToUser(membership.getUserId().toString(), MESSAGES_QUEUE, message));
    }
}
