package com.romanosadchyi.smp.music_app.rest;

import com.romanosadchyi.smp.music_app.model.dto.request.BroadcastSyncMessage;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
public class BroadcastController {

    @MessageMapping("/broadcast/{broadcastId}/sync")
    @SendTo("/topic/broadcast/{broadcastId}")
    public BroadcastSyncMessage syncBroadcast(
            @DestinationVariable UUID broadcastId,
            BroadcastSyncMessage message
    ) {
        return message;
    }
}
