package com.romanosadchyi.smp.music_app.users.internal.exception;

import com.romanosadchyi.smp.music_app.shared.exception.ConflictException;
import lombok.experimental.StandardException;

@StandardException
public class EmailAlreadyInUseException extends ConflictException {
}
