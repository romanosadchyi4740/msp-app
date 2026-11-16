package com.romanosadchyi.smp.music_app.tabs.internal.service;

import com.romanosadchyi.smp.music_app.storage.external.FileStorage;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabPage;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabResponse;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.GuitarProTab;
import com.romanosadchyi.smp.music_app.tabs.internal.mapping.TabMappingService;
import com.romanosadchyi.smp.music_app.tabs.internal.repository.TabRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Reads tabs: one tab or its file for a user who may read it (see {@link TabResolver}), the user's own tabs and the
 * public tab library.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TabQueryService {

    static final int MAX_PAGE_SIZE = 100;

    TabResolver tabResolver;
    TabRepository tabRepository;
    FileStorage fileStorage;
    TabMappingService tabMappingService;

    @Transactional(readOnly = true)
    public TabResponse getTab(UUID userId, UUID tabId) {
        return tabMappingService.map(tabResolver.getReadableTab(userId, tabId));
    }

    /**
     * A presigned URL of a Guitar Pro tab's file.
     *
     * @throws IllegalArgumentException if the tab is an ASCII tab, which has no file
     */
    @Transactional(readOnly = true)
    public String getFileUrl(UUID userId, UUID tabId) {
        if (!(tabResolver.getReadableTab(userId, tabId) instanceof GuitarProTab guitarProTab)) {
            throw new IllegalArgumentException("An ASCII tab has no file");
        }
        return fileStorage.presignedGetUrl(guitarProTab.getFileKey());
    }

    @Transactional(readOnly = true)
    public List<TabResponse> getMyTabs(UUID userId) {
        return tabMappingService.mapAll(tabRepository.findAllByOwnerIdOrderByCreatedAtDesc(userId));
    }

    /**
     * Public, not hidden tabs by title or artist, newest first; an empty query lists them all. {@code %} and
     * {@code _} in the query match themselves, and a page has at most {@value #MAX_PAGE_SIZE} tabs.
     */
    @Transactional(readOnly = true)
    public TabPage search(String query, int pageNo, int pageSize) {
        PageRequest page = PageRequest.of(pageNo, Math.min(pageSize, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return tabMappingService.mapPage(tabRepository.searchPublic(escapeLike(query), page));
    }

    private static String escapeLike(String query) {
        String stripped = query != null ? query.strip() : "";
        return stripped.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
