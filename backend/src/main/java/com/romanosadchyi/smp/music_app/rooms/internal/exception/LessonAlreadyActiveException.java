package com.romanosadchyi.smp.music_app.rooms.internal.exception;

import com.romanosadchyi.smp.music_app.shared.exception.ConflictException;
import lombok.experimental.StandardException;

@StandardException
public class LessonAlreadyActiveException extends ConflictException {
}
