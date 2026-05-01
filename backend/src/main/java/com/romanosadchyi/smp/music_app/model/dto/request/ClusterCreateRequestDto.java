package com.romanosadchyi.smp.music_app.model.dto.request;

import lombok.Builder;

import java.util.Set;
import java.util.UUID;

@Builder
public record ClusterCreateRequestDto(
        String groupName,
        Set<UUID> participantIds
) {}