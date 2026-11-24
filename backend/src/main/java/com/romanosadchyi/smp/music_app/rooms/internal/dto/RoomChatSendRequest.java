package com.romanosadchyi.smp.music_app.rooms.internal.dto;

/**
 * A chat message a participant sends to {@code /app/rooms/{roomId}/chat} (KAN-51).
 *
 * @param content 1 to 2000 characters after trimming
 */
public record RoomChatSendRequest(String content) {
}
