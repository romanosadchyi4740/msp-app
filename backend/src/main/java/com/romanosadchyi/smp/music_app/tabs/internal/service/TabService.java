package com.romanosadchyi.smp.music_app.tabs.internal.service;

import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.storage.external.FileStorage;
import com.romanosadchyi.smp.music_app.tabs.internal.dto.AsciiTabCreateDto;
import com.romanosadchyi.smp.music_app.tabs.internal.dto.GuitarProTabUploadDto;
import com.romanosadchyi.smp.music_app.tabs.internal.dto.TabResponseDto;
import com.romanosadchyi.smp.music_app.tabs.internal.dto.TabUpdateDto;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.AsciiTab;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.GuitarProTab;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.Tab;
import com.romanosadchyi.smp.music_app.tabs.internal.exception.TabNotFoundException;
import com.romanosadchyi.smp.music_app.tabs.internal.mapping.TabMappingService;
import com.romanosadchyi.smp.music_app.tabs.internal.repository.TabRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A user's own tabs and the public tab library. A tab is visible to its owner, and to others only while it is public
 * and not hidden; to everyone else it answers as not found, so private tabs do not reveal that they exist. Only the
 * owner changes or deletes a tab.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TabService {

    static final int MAX_PAGE_SIZE = 100;

    TabRepository tabRepository;
    TabValidator tabValidator;
    TabMappingService tabMappingService;
    FileStorage fileStorage;

    /**
     * Saves the tab first, so the database checks it before the file is stored; a failed upload rolls the row back.
     */
    @Transactional
    public TabResponseDto uploadGuitarPro(UUID ownerId, GuitarProTabUploadDto dto, MultipartFile file) {
        tabValidator.requireRightsConfirmed(dto.rightsConfirmed());
        String title = stripToNull(dto.title());
        String artist = stripToNull(dto.artist());
        tabValidator.validateMetadata(title, artist, dto.tempoBpm(), dto.timeSignature());
        String extension = tabValidator.validateGuitarProFile(file);

        GuitarProTab tab = tabRepository.saveAndFlush(GuitarProTab.builder()
                .ownerId(ownerId)
                .title(title)
                .artist(artist)
                .tempoBpm(dto.tempoBpm())
                .timeSignature(dto.timeSignature())
                .isPublic(Boolean.TRUE.equals(dto.isPublic()))
                .rightsConfirmed(true)
                .fileKey("tabs/" + UUID.randomUUID() + "." + extension)
                .gpVersion(extension.toUpperCase(Locale.ROOT))
                .build());
        fileStorage.upload(file, tab.getFileKey());
        log.info("User {} uploaded Guitar Pro tab {}", ownerId, tab.getId());
        return tabMappingService.map(tab);
    }

    @Transactional
    public TabResponseDto createAscii(UUID ownerId, AsciiTabCreateDto dto) {
        tabValidator.requireRightsConfirmed(dto.rightsConfirmed());
        String title = stripToNull(dto.title());
        String artist = stripToNull(dto.artist());
        tabValidator.validateMetadata(title, artist, dto.tempoBpm(), dto.timeSignature());
        tabValidator.validateRawText(dto.rawText());

        AsciiTab tab = tabRepository.save(AsciiTab.builder()
                .ownerId(ownerId)
                .title(title)
                .artist(artist)
                .tempoBpm(dto.tempoBpm())
                .timeSignature(dto.timeSignature())
                .isPublic(Boolean.TRUE.equals(dto.isPublic()))
                .rightsConfirmed(true)
                .rawText(dto.rawText())
                .build());
        log.info("User {} created ASCII tab {}", ownerId, tab.getId());
        return tabMappingService.map(tab);
    }

    /**
     * @throws TabNotFoundException       if there is no such tab
     * @throws UserNotAuthorizedException if the tab is someone else's
     * @throws IllegalArgumentException   if the metadata is invalid, an ASCII tab has no text or a Guitar Pro tab
     *                                    is sent text
     */
    @Transactional
    public TabResponseDto update(UUID userId, UUID tabId, TabUpdateDto dto) {
        Tab tab = getOwnTab(userId, tabId);
        String title = stripToNull(dto.title());
        String artist = stripToNull(dto.artist());
        tabValidator.validateMetadata(title, artist, dto.tempoBpm(), dto.timeSignature());
        if (tab instanceof AsciiTab asciiTab) {
            tabValidator.validateRawText(dto.rawText());
            asciiTab.setRawText(dto.rawText());
        } else if (dto.rawText() != null) {
            throw new IllegalArgumentException("A Guitar Pro tab has no text to change");
        }

        tab.setTitle(title);
        tab.setArtist(artist);
        tab.setTempoBpm(dto.tempoBpm());
        tab.setTimeSignature(dto.timeSignature());
        tab.setPublic(Boolean.TRUE.equals(dto.isPublic()));
        return tabMappingService.map(tabRepository.saveAndFlush(tab));
    }

    /**
     * Deletes the row before the file, so a row the database refuses to delete keeps its file.
     */
    @Transactional
    public void delete(UUID userId, UUID tabId) {
        Tab tab = getOwnTab(userId, tabId);
        tabRepository.delete(tab);
        tabRepository.flush();
        if (tab instanceof GuitarProTab guitarProTab) {
            fileStorage.delete(guitarProTab.getFileKey());
        }
        log.info("User {} deleted tab {}", userId, tabId);
    }

    @Transactional(readOnly = true)
    public TabResponseDto getTab(UUID userId, UUID tabId) {
        return tabMappingService.map(getReadableTab(userId, tabId));
    }

    /**
     * A presigned URL of a Guitar Pro tab's file.
     *
     * @throws IllegalArgumentException if the tab is an ASCII tab, which has no file
     */
    @Transactional(readOnly = true)
    public String getFileUrl(UUID userId, UUID tabId) {
        if (!(getReadableTab(userId, tabId) instanceof GuitarProTab guitarProTab)) {
            throw new IllegalArgumentException("An ASCII tab has no file");
        }
        return fileStorage.presignedGetUrl(guitarProTab.getFileKey());
    }

    @Transactional(readOnly = true)
    public List<TabResponseDto> getMyTabs(UUID userId) {
        return tabMappingService.mapAll(tabRepository.findAllByOwnerIdOrderByCreatedAtDesc(userId));
    }

    /**
     * Public, not hidden tabs by title or artist, newest first; an empty query lists them all. {@code %} and
     * {@code _} in the query match themselves, and a page has at most {@value #MAX_PAGE_SIZE} tabs.
     */
    @Transactional(readOnly = true)
    public Page<TabResponseDto> search(String query, int pageNo, int pageSize) {
        PageRequest page = PageRequest.of(pageNo, Math.min(pageSize, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return tabMappingService.mapPage(tabRepository.searchPublic(escapeLike(query), page));
    }

    private static String stripToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String escapeLike(String query) {
        String stripped = query != null ? query.strip() : "";
        return stripped.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private Tab getOwnTab(UUID userId, UUID tabId) {
        Tab tab = tabRepository.findById(tabId)
                .orElseThrow(() -> new TabNotFoundException("Tab not found"));
        if (!tab.getOwnerId().equals(userId)) {
            throw new UserNotAuthorizedException("You can only change your own tabs");
        }
        return tab;
    }

    private Tab getReadableTab(UUID userId, UUID tabId) {
        return tabRepository.findById(tabId)
                .filter(tab -> tab.getOwnerId().equals(userId) || (tab.isPublic() && !tab.isHidden()))
                .orElseThrow(() -> new TabNotFoundException("Tab not found"));
    }
}
