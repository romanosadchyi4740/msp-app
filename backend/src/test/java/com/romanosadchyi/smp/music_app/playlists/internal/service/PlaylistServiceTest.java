package com.romanosadchyi.smp.music_app.playlists.internal.service;

import com.romanosadchyi.smp.music_app.playlists.internal.dto.PlaylistCreateDto;
import com.romanosadchyi.smp.music_app.playlists.internal.dto.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.playlists.internal.entity.Playlist;
import com.romanosadchyi.smp.music_app.playlists.internal.mapping.PlaylistMappingService;
import com.romanosadchyi.smp.music_app.playlists.internal.repository.PlaylistRepository;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

    @Mock
    PlaylistRepository playlistRepository;

    @Mock
    PlaylistMappingService playlistMappingService;

    @InjectMocks
    PlaylistService playlistService;

    @Test
    void createPlaylist_Success() {
        UUID userId = UUID.randomUUID();
        PlaylistCreateDto dto = new PlaylistCreateDto("Title", "Desc", true);

        when(playlistRepository.save(any(Playlist.class))).thenAnswer(i -> i.getArgument(0));
        when(playlistMappingService.map(any(Playlist.class))).thenReturn(new PlaylistResponseDto(null, "Title", "Desc", userId, true, null, null, 0, null, null, null));

        PlaylistResponseDto res = playlistService.createPlaylist(userId, dto);

        assertNotNull(res);
        assertEquals("Title", res.title());
        verify(playlistRepository).save(any(Playlist.class));
    }

    @Test
    void getPlaylistById_PrivateAccessNotOwner_ThrowsException() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID playlistId = UUID.randomUUID();

        Playlist playlist = Playlist.builder().id(playlistId).ownerId(ownerId).isPublic(false).build();

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));

        assertThrows(UserNotAuthorizedException.class, () -> playlistService.getPlaylistById(playlistId, otherUserId));
    }

    @Test
    void toggleVisibility_UserIsOwner_Toggles() {
        UUID ownerId = UUID.randomUUID();
        UUID playlistId = UUID.randomUUID();
        Playlist playlist = Playlist.builder().id(playlistId).ownerId(ownerId).isPublic(false).build();

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
        when(playlistRepository.save(any(Playlist.class))).thenAnswer(i -> i.getArgument(0));

        playlistService.toggleVisibility(playlistId, ownerId);

        assertTrue(playlist.isPublic());
        verify(playlistRepository).save(playlist);
    }

    @Test
    void deletePlaylist_UserIsOwner_DetachesAndDeletes() {
        UUID ownerId = UUID.randomUUID();
        UUID playlistId = UUID.randomUUID();
        Playlist playlist = Playlist.builder().id(playlistId).ownerId(ownerId).build();

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));

        playlistService.deletePlaylist(playlistId, ownerId);

        verify(playlistRepository).detachParentPlaylist(playlist);
        verify(playlistRepository).delete(playlist);
    }

    @Test
    void getMyPlaylists_ReturnsList() {
        UUID userId = UUID.randomUUID();
        List<Playlist> playlists = List.of(new Playlist());
        when(playlistRepository.findAllByOwnerId(userId)).thenReturn(playlists);
        when(playlistMappingService.mapAll(playlists)).thenReturn(List.of(response(userId)));

        List<PlaylistResponseDto> result = playlistService.getMyPlaylists(userId);

        assertEquals(1, result.size());
        verify(playlistRepository).findAllByOwnerId(userId);
        verify(playlistMappingService, never()).map(any(Playlist.class));
    }

    @Test
    void getUserPublicPlaylists_ReturnsList() {
        UUID userId = UUID.randomUUID();
        List<Playlist> playlists = List.of(new Playlist());
        when(playlistRepository.findAllByOwnerIdAndIsPublicTrue(userId)).thenReturn(playlists);
        when(playlistMappingService.mapAll(playlists)).thenReturn(List.of(response(userId)));

        List<PlaylistResponseDto> result = playlistService.getUserPublicPlaylists(userId);

        assertEquals(1, result.size());
        verify(playlistRepository).findAllByOwnerIdAndIsPublicTrue(userId);
        verify(playlistMappingService, never()).map(any(Playlist.class));
    }

    private static PlaylistResponseDto response(UUID ownerId) {
        return new PlaylistResponseDto(UUID.randomUUID(), "Title", null, ownerId, true, null, List.of(), 0, null, null,
                null);
    }
}
