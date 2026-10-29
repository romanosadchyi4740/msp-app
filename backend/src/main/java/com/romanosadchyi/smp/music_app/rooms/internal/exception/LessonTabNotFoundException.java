package com.romanosadchyi.smp.music_app.rooms.internal.exception;

import com.romanosadchyi.smp.music_app.shared.exception.NotFoundException;
import lombok.experimental.StandardException;

/**
 * The tab chosen for a lesson does not exist or the host may not read it.
 */
@StandardException
public class LessonTabNotFoundException extends NotFoundException {
}
