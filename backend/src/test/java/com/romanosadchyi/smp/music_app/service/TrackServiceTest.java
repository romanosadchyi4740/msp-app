package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.UserNotFoundException;
import com.romanosadchyi.smp.music_app.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.model.dto.request.TrackCreateDto;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.model.enums.UserRole;
import com.romanosadchyi.smp.music_app.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.repository.UserRepository;
import com.romanosadchyi.smp.music_app.service.mapping.TrackMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackServiceTest {

    @Mock
    private TrackRepository trackRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private MinioService minioService;

    @Mock
    private TrackMappingService trackMappingService;

    @InjectMocks
    private TrackService trackService;

    private User artist;
    private User listener;
    private TrackCreateDto trackDto;
    private MultipartFile mockFile;

    @BeforeEach
    void setUp() {
        UUID artistId = UUID.randomUUID();
        artist = User.builder()
                .id(artistId)
                .role(UserRole.ROLE_ARTIST)
                .build();

        listener = User.builder()
                .id(UUID.randomUUID())
                .role(UserRole.ROLE_USER)
                .build();

        trackDto = new TrackCreateDto("Song", 180, null, null, 1);
        mockFile = mock(MultipartFile.class);
//        when(mockFile.getOriginalFilename()).thenReturn("song.mp3");
    }

    @Test
    void createTrack_ShouldSucceed_WhenUserIsArtist() {
        when(userRepository.findById(artist.getId())).thenReturn(Optional.of(artist));
        when(trackMappingService.map(any(TrackCreateDto.class))).thenReturn(new Track());
        when(trackRepository.save(any(Track.class))).thenReturn(Track.builder().id(UUID.randomUUID()).build());

        trackService.createTrack(artist.getId(), trackDto, mockFile);

        verify(trackRepository, times(2)).save(any(Track.class));
        verify(minioService).uploadFile(eq(mockFile), anyString());
    }

    @Test
    void createTrack_ShouldThrowException_WhenUserIsNotArtist() {
        when(userRepository.findById(listener.getId())).thenReturn(Optional.of(listener));

        assertThrows(UserNotAuthorizedException.class, () -> 
                trackService.createTrack(listener.getId(), trackDto, mockFile));
    }

    @Test
    void createTrack_ShouldThrowException_WhenUserNotFound() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> 
                trackService.createTrack(UUID.randomUUID(), trackDto, mockFile));
    }

}
