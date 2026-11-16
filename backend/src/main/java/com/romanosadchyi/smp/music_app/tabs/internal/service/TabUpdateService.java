package com.romanosadchyi.smp.music_app.tabs.internal.service;

import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabResponse;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabUpdateRequest;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.AsciiTab;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.Tab;
import com.romanosadchyi.smp.music_app.tabs.internal.exception.TabNotFoundException;
import com.romanosadchyi.smp.music_app.tabs.internal.mapping.TabMappingService;
import com.romanosadchyi.smp.music_app.tabs.internal.parsing.AsciiTabParser;
import com.romanosadchyi.smp.music_app.tabs.internal.repository.TabRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Changes the metadata, and the text of an ASCII tab, of a tab the user owns.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TabUpdateService {

    TabResolver tabResolver;
    TabValidator tabValidator;
    AsciiTabParser asciiTabParser;
    TabRepository tabRepository;
    TabMappingService tabMappingService;

    /**
     * @throws TabNotFoundException       if there is no such tab
     * @throws UserNotAuthorizedException if the tab is someone else's
     * @throws IllegalArgumentException   if the metadata is invalid, an ASCII tab has no text or text that is not
     *                                    a tab, or a Guitar Pro tab is sent text
     */
    @Transactional
    public TabResponse update(UUID userId, UUID tabId, TabUpdateRequest request) {
        Tab tab = tabResolver.getOwnTab(userId, tabId);
        String title = TabText.stripToNull(request.getTitle());
        String artist = TabText.stripToNull(request.getArtist());
        tabValidator.validateMetadata(title, artist, request.getTempoBpm(), request.getTimeSignature());
        if (tab instanceof AsciiTab asciiTab) {
            tabValidator.validateRawText(request.getRawText());
            asciiTabParser.parse(request.getRawText());
            asciiTab.setRawText(request.getRawText());
        } else if (request.getRawText() != null) {
            throw new IllegalArgumentException("A Guitar Pro tab has no text to change");
        }

        tab.setTitle(title);
        tab.setArtist(artist);
        tab.setTempoBpm(request.getTempoBpm());
        tab.setTimeSignature(request.getTimeSignature());
        tab.setPublic(Boolean.TRUE.equals(request.getIsPublic()));
        return tabMappingService.map(tabRepository.saveAndFlush(tab));
    }
}
