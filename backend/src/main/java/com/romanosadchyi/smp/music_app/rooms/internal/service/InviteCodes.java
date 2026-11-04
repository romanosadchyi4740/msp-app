package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

/**
 * Invite codes no room has yet.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InviteCodes {

    static final int MAX_ATTEMPTS = 5;

    InviteCodeGenerator inviteCodeGenerator;
    RoomRepository roomRepository;

    /**
     * A code no room has yet; the unique constraint still rejects one taken by a concurrent transaction.
     */
    public String newCode() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = inviteCodeGenerator.generate();
            if (!roomRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a free invite code");
    }
}
