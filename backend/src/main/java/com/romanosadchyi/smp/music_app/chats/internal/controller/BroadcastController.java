package com.romanosadchyi.smp.music_app.chats.internal.controller;

import com.romanosadchyi.smp.music_app.chats.internal.dto.BroadcastSyncMessage;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.util.UUID;

/**
 * Relays listening-broadcast messages to everyone listening. The chat id in the destination lets the STOMP guard
 * open a broadcast only to the members of the chat it was announced in.
 */
@Controller
public class BroadcastController {

    @MessageMapping("/broadcast/{chatId}/{broadcastId}/sync")
    @SendTo("/topic/broadcast/{chatId}/{broadcastId}")
    public BroadcastSyncMessage syncBroadcast(
            @DestinationVariable UUID chatId,
            @DestinationVariable UUID broadcastId,
            BroadcastSyncMessage message
    ) {
        return message;
    }
}
