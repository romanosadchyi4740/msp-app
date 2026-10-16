package com.romanosadchyi.smp.music_app.model.dto.response;

public record PlaylistSyncResponseDto(
        PlaylistResponseDto playlist,
        int newTracksAdded
) {
}
