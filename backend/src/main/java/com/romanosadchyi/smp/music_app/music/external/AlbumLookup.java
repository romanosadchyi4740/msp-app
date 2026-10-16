package com.romanosadchyi.smp.music_app.music.external;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Read access to albums for other modules.
 */
public interface AlbumLookup {

    Page<AlbumResponseDto> searchAlbums(String titleQuery, Pageable pageable);
}
