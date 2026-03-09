package com.romanosadchyi.smp.music_app.model.dto.event;

import java.util.UUID;

public record UserCreatedEvent(
        UUID id,
        String email,
        String displayName,
        String avatarUrl
) {}
