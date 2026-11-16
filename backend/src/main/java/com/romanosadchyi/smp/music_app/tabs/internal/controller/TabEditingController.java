package com.romanosadchyi.smp.music_app.tabs.internal.controller;

import com.romanosadchyi.smp.music_app.shared.security.CurrentUser;
import com.romanosadchyi.smp.music_app.tabs.internal.api.TabEditingApi;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabResponse;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabUpdateRequest;
import com.romanosadchyi.smp.music_app.tabs.internal.service.TabDeletionService;
import com.romanosadchyi.smp.music_app.tabs.internal.service.TabUpdateService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Changing and deleting the user's own tabs: implements the interface generated from the {@code TabEditing} tag of
 * {@code openapi/tabs.yaml}, which holds the mappings; the current user comes from {@link CurrentUser}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TabEditingController implements TabEditingApi {

    TabUpdateService tabUpdateService;
    TabDeletionService tabDeletionService;

    @Override
    public ResponseEntity<TabResponse> updateTab(UUID id, TabUpdateRequest request) {
        UUID userId = CurrentUser.get().id();
        log.info("User {} updating tab {}", userId, id);
        return ResponseEntity.ok(tabUpdateService.update(userId, id, request));
    }

    @Override
    public ResponseEntity<Void> deleteTab(UUID id) {
        UUID userId = CurrentUser.get().id();
        log.info("User {} deleting tab {}", userId, id);
        tabDeletionService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
