package com.romanosadchyi.smp.music_app.tabs.internal.controller;

import com.romanosadchyi.smp.music_app.shared.security.CurrentUser;
import com.romanosadchyi.smp.music_app.tabs.internal.api.TabsApi;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabPage;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabResponse;
import com.romanosadchyi.smp.music_app.tabs.internal.service.TabQueryService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Reading tabs: implements the interface generated from the {@code Tabs} tag of {@code openapi/tabs.yaml}, which
 * holds the mappings; the current user comes from {@link CurrentUser}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TabController implements TabsApi {

    TabQueryService tabQueryService;

    @Override
    public ResponseEntity<List<TabResponse>> getMyTabs() {
        UUID userId = CurrentUser.get().id();
        log.info("Getting tabs of user: {}", userId);
        return ResponseEntity.ok(tabQueryService.getMyTabs(userId));
    }

    @Override
    public ResponseEntity<TabPage> searchTabs(String q, Integer pageNo, Integer pageSize) {
        log.info("Searching tabs with query: {}", q);
        return ResponseEntity.ok(tabQueryService.search(q, pageNo, pageSize));
    }

    @Override
    public ResponseEntity<TabResponse> getTab(UUID id) {
        UUID userId = CurrentUser.get().id();
        log.info("User {} getting tab {}", userId, id);
        return ResponseEntity.ok(tabQueryService.getTab(userId, id));
    }

    @Override
    public ResponseEntity<String> getTabFileUrl(UUID id) {
        UUID userId = CurrentUser.get().id();
        log.info("User {} getting the file URL of tab {}", userId, id);
        return ResponseEntity.ok(tabQueryService.getFileUrl(userId, id));
    }
}
