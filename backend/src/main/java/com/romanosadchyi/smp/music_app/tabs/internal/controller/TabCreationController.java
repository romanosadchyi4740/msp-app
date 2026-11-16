package com.romanosadchyi.smp.music_app.tabs.internal.controller;

import com.romanosadchyi.smp.music_app.shared.security.CurrentUser;
import com.romanosadchyi.smp.music_app.tabs.internal.api.TabCreationApi;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.AsciiTabCreateRequest;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.AsciiTabPreview;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.AsciiTabPreviewRequest;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabResponse;
import com.romanosadchyi.smp.music_app.tabs.internal.dto.GuitarProTabUploadDto;
import com.romanosadchyi.smp.music_app.tabs.internal.service.AsciiTabPreviewService;
import com.romanosadchyi.smp.music_app.tabs.internal.service.AsciiTabService;
import com.romanosadchyi.smp.music_app.tabs.internal.service.GuitarProTabUploadService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Creating tabs and previewing ASCII tab text: implements the interface generated from the {@code TabCreation} tag of
 * {@code openapi/tabs.yaml}, which holds the mappings; the current user comes from {@link CurrentUser}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TabCreationController implements TabCreationApi {

    GuitarProTabUploadService guitarProTabUploadService;
    AsciiTabService asciiTabService;
    AsciiTabPreviewService asciiTabPreviewService;

    @Override
    public ResponseEntity<TabResponse> uploadGuitarProTab(MultipartFile file, String title, String artist,
                                                          Integer tempoBpm, String timeSignature, Boolean isPublic,
                                                          Boolean rightsConfirmed) {
        UUID userId = CurrentUser.get().id();
        log.info("Uploading Guitar Pro tab for user: {}", userId);
        GuitarProTabUploadDto dto =
                new GuitarProTabUploadDto(title, artist, tempoBpm, timeSignature, isPublic, rightsConfirmed);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(guitarProTabUploadService.uploadGuitarPro(userId, dto, file));
    }

    @Override
    public ResponseEntity<TabResponse> createAsciiTab(AsciiTabCreateRequest request) {
        UUID userId = CurrentUser.get().id();
        log.info("Creating ASCII tab for user: {}", userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(asciiTabService.createAscii(userId, request));
    }

    @Override
    public ResponseEntity<AsciiTabPreview> previewAsciiTab(AsciiTabPreviewRequest request) {
        return ResponseEntity.ok(asciiTabPreviewService.previewAscii(request));
    }
}
