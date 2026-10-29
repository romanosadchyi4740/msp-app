package com.romanosadchyi.smp.music_app.tabs.internal.controller;

import com.romanosadchyi.smp.music_app.shared.security.CurrentUser;
import com.romanosadchyi.smp.music_app.tabs.internal.api.TabsApi;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.AsciiTabCreateRequest;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.AsciiTabPreview;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.AsciiTabPreviewRequest;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabPage;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabResponse;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabUpdateRequest;
import com.romanosadchyi.smp.music_app.tabs.internal.dto.GuitarProTabUploadDto;
import com.romanosadchyi.smp.music_app.tabs.internal.service.TabService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Implements the interface generated from {@code openapi/tabs.yaml}, which holds the mappings; the current user
 * comes from {@link CurrentUser}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TabController implements TabsApi {

    TabService tabService;

    @Override
    public ResponseEntity<TabResponse> uploadGuitarProTab(MultipartFile file, String title, String artist,
                                                          Integer tempoBpm, String timeSignature, Boolean isPublic,
                                                          Boolean rightsConfirmed) {
        UUID userId = CurrentUser.get().id();
        log.info("Uploading Guitar Pro tab for user: {}", userId);
        GuitarProTabUploadDto dto =
                new GuitarProTabUploadDto(title, artist, tempoBpm, timeSignature, isPublic, rightsConfirmed);
        return ResponseEntity.status(HttpStatus.CREATED).body(tabService.uploadGuitarPro(userId, dto, file));
    }

    @Override
    public ResponseEntity<TabResponse> createAsciiTab(AsciiTabCreateRequest request) {
        UUID userId = CurrentUser.get().id();
        log.info("Creating ASCII tab for user: {}", userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(tabService.createAscii(userId, request));
    }

    @Override
    public ResponseEntity<AsciiTabPreview> previewAsciiTab(AsciiTabPreviewRequest request) {
        return ResponseEntity.ok(tabService.previewAscii(request));
    }

    @Override
    public ResponseEntity<TabResponse> updateTab(UUID id, TabUpdateRequest request) {
        UUID userId = CurrentUser.get().id();
        log.info("User {} updating tab {}", userId, id);
        return ResponseEntity.ok(tabService.update(userId, id, request));
    }

    @Override
    public ResponseEntity<Void> deleteTab(UUID id) {
        UUID userId = CurrentUser.get().id();
        log.info("User {} deleting tab {}", userId, id);
        tabService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<TabResponse>> getMyTabs() {
        UUID userId = CurrentUser.get().id();
        log.info("Getting tabs of user: {}", userId);
        return ResponseEntity.ok(tabService.getMyTabs(userId));
    }

    @Override
    public ResponseEntity<TabPage> searchTabs(String q, Integer pageNo, Integer pageSize) {
        log.info("Searching tabs with query: {}", q);
        return ResponseEntity.ok(tabService.search(q, pageNo, pageSize));
    }

    @Override
    public ResponseEntity<TabResponse> getTab(UUID id) {
        UUID userId = CurrentUser.get().id();
        log.info("User {} getting tab {}", userId, id);
        return ResponseEntity.ok(tabService.getTab(userId, id));
    }

    @Override
    public ResponseEntity<String> getTabFileUrl(UUID id) {
        UUID userId = CurrentUser.get().id();
        log.info("User {} getting the file URL of tab {}", userId, id);
        return ResponseEntity.ok(tabService.getFileUrl(userId, id));
    }
}
