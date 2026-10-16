package com.romanosadchyi.smp.music_app.playlists.internal.dto;

public record PlaylistSyncResponseDto(
        PlaylistResponseDto playlist,
        int newTracksAdded
) {
}
