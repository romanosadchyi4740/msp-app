package com.romanosadchyi.smp.music_app.search.internal.dto;

import com.romanosadchyi.smp.music_app.music.external.AlbumResponseDto;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.users.external.UserResponseDto;
import lombok.Builder;
import org.springframework.data.domain.Page;

@Builder
public record GlobalSearchResponseDto(
    Page<UserResponseDto> users,
    Page<AlbumResponseDto> albums,
    Page<TrackResponseDto> tracks
) {
}
