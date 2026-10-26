package com.romanosadchyi.smp.music_app.tabs.internal.mapping;

import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabFormat;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabPage;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabResponse;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.AsciiTab;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.GuitarProTab;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.Tab;
import com.romanosadchyi.smp.music_app.tabs.internal.parsing.AlphaTexWriter;
import com.romanosadchyi.smp.music_app.tabs.internal.parsing.AsciiTabParser;
import com.romanosadchyi.smp.music_app.tabs.internal.parsing.AsciiTabScore;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserSummary;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maps tabs to the API models generated from {@code openapi/tabs.yaml}. Owner names come from {@link UserLookup}; the
 * list and page variants look up all owners in one call and leave out the text and alphaTex of ASCII tabs, which
 * only a single tab's response carries.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TabMappingService {

    UserLookup userLookup;
    AsciiTabParser asciiTabParser;
    AlphaTexWriter alphaTexWriter;

    /**
     * A single tab; an ASCII tab also carries its text and its alphaTex.
     */
    public TabResponse map(Tab tab) {
        UserSummary owner = userLookup.findSummary(tab.getOwnerId()).orElse(null);
        TabResponse response = toResponse(tab, owner);
        if (tab instanceof AsciiTab asciiTab) {
            response.rawText(asciiTab.getRawText()).alphaTex(alphaTex(asciiTab));
        }
        return response;
    }

    /**
     * {@code null} if the text is not a tab, which is only possible for text stored before it was parsed on save.
     */
    private String alphaTex(AsciiTab tab) {
        try {
            AsciiTabScore score = asciiTabParser.parse(tab.getRawText());
            return alphaTexWriter.write(score, tab.getTitle(), tab.getArtist(), tab.getTempoBpm(),
                    tab.getTimeSignature());
        } catch (IllegalArgumentException e) {
            log.warn("Tab {} has text that is not a tab: {}", tab.getId(), e.getMessage());
            return null;
        }
    }

    public List<TabResponse> mapAll(Collection<Tab> tabs) {
        Map<UUID, UserSummary> owners = ownersOf(tabs);
        return tabs.stream()
                .map(tab -> toResponse(tab, owners.get(tab.getOwnerId())))
                .toList();
    }

    public TabPage mapPage(Page<Tab> tabs) {
        return new TabPage()
                .content(mapAll(tabs.getContent()))
                .number(tabs.getNumber())
                .size(tabs.getSize())
                .numberOfElements(tabs.getNumberOfElements())
                .totalElements(tabs.getTotalElements())
                .totalPages(tabs.getTotalPages())
                .first(tabs.isFirst())
                .last(tabs.isLast())
                .empty(tabs.isEmpty());
    }

    private Map<UUID, UserSummary> ownersOf(Collection<Tab> tabs) {
        return userLookup.findSummaries(tabs.stream().map(Tab::getOwnerId).distinct().toList());
    }

    private static TabResponse toResponse(Tab tab, UserSummary owner) {
        return new TabResponse()
                .id(tab.getId())
                .format(tab instanceof GuitarProTab ? TabFormat.GUITAR_PRO : TabFormat.ASCII)
                .title(tab.getTitle())
                .artist(tab.getArtist())
                .ownerId(tab.getOwnerId())
                .ownerName(owner != null ? owner.displayName() : null)
                .tempoBpm(tab.getTempoBpm())
                .timeSignature(tab.getTimeSignature())
                .isPublic(tab.isPublic())
                .hidden(tab.isHidden())
                .gpVersion(tab instanceof GuitarProTab guitarProTab ? guitarProTab.getGpVersion() : null)
                .createdAt(tab.getCreatedAt())
                .updatedAt(tab.getUpdatedAt());
    }
}
