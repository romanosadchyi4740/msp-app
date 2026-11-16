package com.romanosadchyi.smp.music_app.tabs.internal.service;

import com.romanosadchyi.smp.music_app.storage.external.FileStorage;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.GuitarProTab;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.Tab;
import com.romanosadchyi.smp.music_app.tabs.internal.repository.TabRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Deletes a tab the user owns, with its file.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TabDeletionService {

    TabResolver tabResolver;
    TabRepository tabRepository;
    FileStorage fileStorage;

    /**
     * Deletes the row before the file, so a row the database refuses to delete keeps its file.
     */
    @Transactional
    public void delete(UUID userId, UUID tabId) {
        Tab tab = tabResolver.getOwnTab(userId, tabId);
        tabRepository.delete(tab);
        tabRepository.flush();
        if (tab instanceof GuitarProTab guitarProTab) {
            fileStorage.delete(guitarProTab.getFileKey());
        }
        log.info("User {} deleted tab {}", userId, tabId);
    }
}
