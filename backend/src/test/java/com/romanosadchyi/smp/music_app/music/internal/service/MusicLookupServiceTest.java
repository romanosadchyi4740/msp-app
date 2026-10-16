package com.romanosadchyi.smp.music_app.music.internal.service;

import com.romanosadchyi.smp.music_app.music.external.TrackNotFoundException;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.music.internal.mapping.AlbumMappingService;
import com.romanosadchyi.smp.music_app.music.internal.mapping.TrackMappingService;
import com.romanosadchyi.smp.music_app.music.internal.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.music.internal.repository.TrackRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MusicLookupServiceTest {

    @Mock
    TrackRepository trackRepository;

    @Mock
    AlbumRepository albumRepository;

    @Mock
    TrackMappingService trackMappingService;

    @Mock
    AlbumMappingService albumMappingService;

    @InjectMocks
    MusicLookupService lookup;

    @Test
    void getTrack_Unknown_Throws() {
        UUID id = UUID.randomUUID();
        when(trackRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lookup.getTrack(id)).isInstanceOf(TrackNotFoundException.class);
    }

    @Test
    void findTracks_KeepsRequestedOrderAndSkipsUnknownIds() {
        Track first = Track.builder().id(UUID.randomUUID()).build();
        Track second = Track.builder().id(UUID.randomUUID()).build();
        UUID unknown = UUID.randomUUID();
        List<UUID> ids = List.of(second.getId(), unknown, first.getId());
        List<TrackResponseDto> mapped = List.of(TrackResponseDto.builder().id(second.getId()).build(),
                TrackResponseDto.builder().id(first.getId()).build());
        when(trackRepository.findAllById(ids)).thenReturn(List.of(first, second));
        when(trackMappingService.mapAll(List.of(second, first))).thenReturn(mapped);

        assertThat(lookup.findTracks(ids)).isEqualTo(mapped);
    }
}
