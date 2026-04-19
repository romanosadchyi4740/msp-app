package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.UserNotFoundException;
import com.romanosadchyi.smp.music_app.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.model.dto.request.TrackCreateDto;
import com.romanosadchyi.smp.music_app.model.entity.Album;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.model.enums.UserRole;
import com.romanosadchyi.smp.music_app.repository.AlbumRepository;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.repository.UserRepository;
import com.romanosadchyi.smp.music_app.service.mapping.TrackMappingService;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
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
@FieldDefaults(level = AccessLevel.PRIVATE)
class TrackServiceTest {

    @Mock
    TrackRepository trackRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    AlbumRepository albumRepository;

    @Mock
    MinioService minioService;

    @Mock
    TrackMappingService trackMappingService;

    @InjectMocks
    TrackService trackService;

    User artist;
    User listener;
    TrackCreateDto trackDto;
    MultipartFile mockFile;

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

        trackDto = new TrackCreateDto("Song", 210, null, UUID.randomUUID(), 1);
        mockFile = mock(MultipartFile.class);
//        when(mockFile.getOriginalFilename()).thenReturn("song.mp3");
    }

    @Test
    void createTrack_ShouldSucceed_WhenUserIsArtist() {
        when(userRepository.findById(artist.getId())).thenReturn(Optional.of(artist));
        when(trackMappingService.map(any(TrackCreateDto.class))).thenReturn(new Track());
        when(trackRepository.save(any(Track.class))).thenReturn(Track.builder().id(UUID.randomUUID()).build());
        when(albumRepository.findById(any())).thenReturn(Optional.of(new Album().toBuilder().author(artist).build()));

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
