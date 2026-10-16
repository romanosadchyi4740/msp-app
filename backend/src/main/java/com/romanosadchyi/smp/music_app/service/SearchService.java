package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.dto.response.GlobalSearchResponseDto;
import com.romanosadchyi.smp.music_app.music.external.AlbumLookup;
import com.romanosadchyi.smp.music_app.music.external.TrackLookup;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SearchService {

    UserLookup userLookup;
    TrackLookup trackLookup;
    AlbumLookup albumLookup;

    @Transactional(readOnly = true)
    public GlobalSearchResponseDto globalSearch(String query, int size) {
        var pageable = PageRequest.of(0, size);
        return GlobalSearchResponseDto.builder()
                .users(userLookup.searchProfiles(query, pageable))
                .albums(albumLookup.searchAlbums(query, pageable))
                .tracks(trackLookup.searchTracks(query, pageable))
                .build();
    }
}
