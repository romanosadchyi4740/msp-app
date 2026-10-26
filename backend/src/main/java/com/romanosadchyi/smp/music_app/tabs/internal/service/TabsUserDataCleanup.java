package com.romanosadchyi.smp.music_app.tabs.internal.service;

import com.romanosadchyi.smp.music_app.storage.external.FileStorage;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.GuitarProTab;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.Tab;
import com.romanosadchyi.smp.music_app.tabs.internal.repository.TabRepository;
import com.romanosadchyi.smp.music_app.users.external.UserDataCleanup;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * On account deletion: deletes the user's tabs and their Guitar Pro files.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class TabsUserDataCleanup implements UserDataCleanup {

    TabRepository tabRepository;
    FileStorage fileStorage;

    @Override
    public void deleteUserData(UUID userId) {
        List<Tab> tabs = tabRepository.findAllByOwnerId(userId);
        // Rows first, so rows the database refuses to delete keep their files
        tabRepository.deleteAll(tabs);
        tabRepository.flush();
        for (Tab tab : tabs) {
            if (tab instanceof GuitarProTab guitarProTab) {
                fileStorage.delete(guitarProTab.getFileKey());
            }
        }
    }
}
