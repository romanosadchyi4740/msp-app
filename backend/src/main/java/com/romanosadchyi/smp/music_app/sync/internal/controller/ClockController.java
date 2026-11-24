package com.romanosadchyi.smp.music_app.sync.internal.controller;

import com.romanosadchyi.smp.music_app.sync.internal.dto.TimeSyncRequest;
import com.romanosadchyi.smp.music_app.sync.internal.dto.TimeSyncResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.time.Clock;

/**
 * The server clock for the NTP-style clock sync of the clients (KAN-41): {@code SEND /app/time} with a
 * {@link TimeSyncRequest} is answered on {@code /user/queue/time} of the asking session only, so the other tabs of
 * the same user do not take it for their own sample. Any signed-in session may ask; the time is not room data.
 */
@Controller
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ClockController {

    Clock clock;

    @MessageMapping("/time")
    @SendToUser(destinations = "/queue/time", broadcast = false)
    public TimeSyncResponse time(@Payload TimeSyncRequest request) {
        return new TimeSyncResponse(request.clientTime(), clock.millis());
    }
}
