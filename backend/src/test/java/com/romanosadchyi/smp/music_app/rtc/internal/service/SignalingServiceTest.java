package com.romanosadchyi.smp.music_app.rtc.internal.service;

import com.romanosadchyi.smp.music_app.rooms.external.RoomMembership;
import com.romanosadchyi.smp.music_app.rtc.internal.dto.SignalMessage;
import com.romanosadchyi.smp.music_app.rtc.internal.dto.SignalType;
import com.romanosadchyi.smp.music_app.rtc.internal.exception.SignalPeerNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignalingServiceTest {

    @Mock
    RoomMembership roomMembership;

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    SignalingService signalingService;

    UUID roomId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    UUID recipientId = UUID.randomUUID();

    @Test
    void relay_BetweenParticipants_GoesToTheRecipientsQueue() {
        bothInTheRoom();

        signalingService.relay(roomId, senderId, signal(SignalType.OFFER, recipientId, "sdp"));

        verify(messagingTemplate).convertAndSendToUser(recipientId.toString(), "/queue/signal",
                new SignalMessage(SignalType.OFFER, roomId, senderId, recipientId, "sdp"));
    }

    @Test
    void relay_ClaimedSenderAndRoom_AreReplacedByTheSessionUserAndTheDestination() {
        bothInTheRoom();
        SignalMessage forged = new SignalMessage(SignalType.ANSWER, UUID.randomUUID(), UUID.randomUUID(), recipientId,
                "sdp");

        signalingService.relay(roomId, senderId, forged);

        verify(messagingTemplate).convertAndSendToUser(recipientId.toString(), "/queue/signal",
                new SignalMessage(SignalType.ANSWER, roomId, senderId, recipientId, "sdp"));
    }

    @Test
    void relay_RecipientNotInTheRoom_IsRejected() {
        when(roomMembership.isParticipant(roomId, senderId)).thenReturn(true);
        when(roomMembership.isParticipant(roomId, recipientId)).thenReturn(false);

        assertThatThrownBy(() -> signalingService.relay(roomId, senderId,
                signal(SignalType.ICE_CANDIDATE, recipientId, "candidate")))
                .isInstanceOf(SignalPeerNotFoundException.class)
                .hasMessage("The recipient is not in this room");
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void relay_SenderNotInTheRoom_IsRejected() {
        when(roomMembership.isParticipant(roomId, senderId)).thenReturn(false);

        assertThatThrownBy(() -> signalingService.relay(roomId, senderId,
                signal(SignalType.OFFER, recipientId, "sdp")))
                .isInstanceOf(SignalPeerNotFoundException.class)
                .hasMessage("Room not found");
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void relay_MissingParts_AreBadRequests() {
        assertThatThrownBy(() -> signalingService.relay(roomId, senderId, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signalingService.relay(roomId, senderId, signal(null, recipientId, "sdp")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A signal type is required");
        assertThatThrownBy(() -> signalingService.relay(roomId, senderId, signal(SignalType.OFFER, null, "sdp")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A recipient is required");
        assertThatThrownBy(() -> signalingService.relay(roomId, senderId,
                signal(SignalType.OFFER, recipientId, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A payload is required");
        assertThatThrownBy(() -> signalingService.relay(roomId, senderId, signal(SignalType.OFFER, recipientId, " ")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A payload is required");
        verifyNoInteractions(roomMembership, messagingTemplate);
    }

    @Test
    void relay_ToItsSender_IsABadRequest() {
        assertThatThrownBy(() -> signalingService.relay(roomId, senderId, signal(SignalType.OFFER, senderId, "sdp")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(roomMembership, messagingTemplate);
    }

    @Test
    void relay_PayloadOverTheLimit_IsABadRequestThatDoesNotQuoteIt() {
        String tooLong = "x".repeat(SignalingService.MAX_PAYLOAD_LENGTH + 1);

        assertThatThrownBy(() -> signalingService.relay(roomId, senderId,
                signal(SignalType.OFFER, recipientId, tooLong)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("xxx");
        verifyNoInteractions(roomMembership, messagingTemplate);
    }

    @Test
    void relay_PayloadAtTheLimit_IsRelayed() {
        bothInTheRoom();
        String longest = "x".repeat(SignalingService.MAX_PAYLOAD_LENGTH);

        signalingService.relay(roomId, senderId, signal(SignalType.OFFER, recipientId, longest));

        verify(messagingTemplate).convertAndSendToUser(recipientId.toString(), "/queue/signal",
                new SignalMessage(SignalType.OFFER, roomId, senderId, recipientId, longest));
    }

    private void bothInTheRoom() {
        when(roomMembership.isParticipant(roomId, senderId)).thenReturn(true);
        when(roomMembership.isParticipant(roomId, recipientId)).thenReturn(true);
    }

    private static SignalMessage signal(SignalType type, UUID toId, String payload) {
        return new SignalMessage(type, null, null, toId, payload);
    }
}
