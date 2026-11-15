package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomMessagePage;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomMessageResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.RoomChatSendRequest;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomMessage;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomClosedException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.mapping.RoomChatMappingService;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomMessageRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * A room's text chat (KAN-51, FR-12): only the room's current participants send and read messages, whose content is
 * stored encrypted. Message text is never logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomChatService {

    static final int MAX_CONTENT_LENGTH = 2000;
    static final int MAX_PAGE_SIZE = 100;

    RoomAccess roomAccess;
    RoomMessageRepository messageRepository;
    RoomChatMappingService mappingService;
    RoomChatNotifier notifier;

    /**
     * Stores the message and sends it to the room's chat topic after the commit.
     *
     * @throws RoomNotFoundException     if there is no such room or the user is not in it
     * @throws RoomClosedException       if the room is closed
     * @throws IllegalArgumentException if the content is missing, blank or longer than 2000 characters
     */
    @Transactional
    public RoomMessageResponse send(UUID userId, UUID roomId, RoomChatSendRequest request) {
        String content = request == null || request.content() == null ? "" : request.content().strip();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("A message needs some text");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("A message has at most " + MAX_CONTENT_LENGTH + " characters");
        }
        RoomParticipant participant = roomAccess.requireParticipant(userId, roomId);
        Room room = participant.getRoom();
        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new RoomClosedException("The room is closed");
        }
        // Flushed at once: Hibernate sets sentAt (@CreationTimestamp) on the insert, and the broadcast needs it
        RoomMessage message = messageRepository.saveAndFlush(RoomMessage.builder()
                .room(room)
                .authorId(userId)
                .content(content)
                .build());
        RoomMessageResponse response = mappingService.map(message);
        notifier.publish(roomId, response);
        log.info("User {} sent message {} in room {}", userId, message.getId(), roomId);
        return response;
    }

    /**
     * A page of the room's messages, newest first; also readable after the room closed.
     *
     * @throws RoomNotFoundException     if there is no such room or the user is not in it
     * @throws IllegalArgumentException if the page number is negative or the size is not 1 to 100
     */
    @Transactional(readOnly = true)
    public RoomMessagePage history(UUID userId, UUID roomId, Integer pageNo, Integer pageSize) {
        int number = pageNo == null ? 0 : pageNo;
        int size = pageSize == null ? 50 : pageSize;
        if (number < 0) {
            throw new IllegalArgumentException("The page number starts at 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("A page holds 1 to " + MAX_PAGE_SIZE + " messages");
        }
        roomAccess.requireParticipant(userId, roomId);
        return mappingService.mapPage(messageRepository.findPageByRoomId(roomId, PageRequest.of(number, size)));
    }
}
