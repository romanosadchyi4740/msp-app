package com.romanosadchyi.smp.music_app.rtc.internal.service;

import com.romanosadchyi.smp.music_app.rooms.external.RoomMembership;
import com.romanosadchyi.smp.music_app.rtc.internal.dto.SignalMessage;
import com.romanosadchyi.smp.music_app.rtc.internal.exception.SignalPeerNotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The WebRTC signaling relay (lab 8, sub-process "Establishing P2P audio"): passes offers, answers and ICE candidates
 * between two participants of the same room so their browsers can connect directly. Nothing is stored, and payloads
 * are never logged: they hold the participants' network addresses.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SignalingService {

    /**
     * An audio-only session description is a few thousand characters and a candidate a few hundred; this leaves room
     * while keeping the relay from carrying arbitrary data.
     */
    static final int MAX_PAYLOAD_LENGTH = 16_000;

    static final String SIGNAL_QUEUE = "/queue/signal";

    RoomMembership roomMembership;
    SimpMessagingTemplate messagingTemplate;

    /**
     * Relays a signal from {@code senderId} to {@code /user/queue/signal} of its recipient. The sender and the room
     * come from the session and the destination, replacing whatever the client claimed.
     *
     * @throws IllegalArgumentException    if the type, recipient or payload is missing, the payload is too long, or
     *                                     the signal is addressed to its sender
     * @throws SignalPeerNotFoundException if the sender or the recipient is not a participant of the room
     */
    public void relay(UUID roomId, UUID senderId, SignalMessage received) {
        if (received == null || received.type() == null) {
            throw new IllegalArgumentException("A signal type is required");
        }
        if (received.toId() == null) {
            throw new IllegalArgumentException("A recipient is required");
        }
        if (received.toId().equals(senderId)) {
            throw new IllegalArgumentException("A signal cannot be addressed to its sender");
        }
        if (received.payload() == null || received.payload().isBlank()) {
            throw new IllegalArgumentException("A payload is required");
        }
        if (received.payload().length() > MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("The payload is longer than " + MAX_PAYLOAD_LENGTH + " characters");
        }
        // The STOMP guard of rooms already lets only participants send here; checked again so the relay does not
        // rely on it
        if (!roomMembership.isParticipant(roomId, senderId)) {
            throw new SignalPeerNotFoundException("Room not found");
        }
        if (!roomMembership.isParticipant(roomId, received.toId())) {
            throw new SignalPeerNotFoundException("The recipient is not in this room");
        }
        SignalMessage signal = new SignalMessage(received.type(), roomId, senderId, received.toId(),
                received.payload());
        messagingTemplate.convertAndSendToUser(signal.toId().toString(), SIGNAL_QUEUE, signal);
        log.debug("Relayed {} from {} to {} in room {}", signal.type(), senderId, signal.toId(), roomId);
    }
}
