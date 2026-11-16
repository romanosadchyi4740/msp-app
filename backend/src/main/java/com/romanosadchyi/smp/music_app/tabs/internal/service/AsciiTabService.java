package com.romanosadchyi.smp.music_app.tabs.internal.service;

import com.romanosadchyi.smp.music_app.tabs.internal.api.model.AsciiTabCreateRequest;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabResponse;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.AsciiTab;
import com.romanosadchyi.smp.music_app.tabs.internal.mapping.TabMappingService;
import com.romanosadchyi.smp.music_app.tabs.internal.parsing.AsciiTabParser;
import com.romanosadchyi.smp.music_app.tabs.internal.repository.TabRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Creates a tab of the user from ASCII tab text.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AsciiTabService {

    TabRepository tabRepository;
    TabValidator tabValidator;
    AsciiTabParser asciiTabParser;
    TabMappingService tabMappingService;

    /**
     * @throws IllegalArgumentException if the input is invalid or the text contains no tab
     */
    @Transactional
    public TabResponse createAscii(UUID ownerId, AsciiTabCreateRequest request) {
        tabValidator.requireRightsConfirmed(request.getRightsConfirmed());
        String title = TabText.stripToNull(request.getTitle());
        String artist = TabText.stripToNull(request.getArtist());
        tabValidator.validateMetadata(title, artist, request.getTempoBpm(), request.getTimeSignature());
        tabValidator.validateRawText(request.getRawText());
        asciiTabParser.parse(request.getRawText());

        AsciiTab tab = tabRepository.save(AsciiTab.builder()
                .ownerId(ownerId)
                .title(title)
                .artist(artist)
                .tempoBpm(request.getTempoBpm())
                .timeSignature(request.getTimeSignature())
                .isPublic(Boolean.TRUE.equals(request.getIsPublic()))
                .rightsConfirmed(true)
                .rawText(request.getRawText())
                .build());
        log.info("User {} created ASCII tab {}", ownerId, tab.getId());
        return tabMappingService.map(tab);
    }
}
