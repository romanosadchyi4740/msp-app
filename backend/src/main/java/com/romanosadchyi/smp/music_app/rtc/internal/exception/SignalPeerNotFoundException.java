package com.romanosadchyi.smp.music_app.rtc.internal.exception;

import com.romanosadchyi.smp.music_app.shared.exception.NotFoundException;
import lombok.experimental.StandardException;

/**
 * The sender or the recipient of a signal is not a participant of the room.
 */
@StandardException
public class SignalPeerNotFoundException extends NotFoundException {
}
