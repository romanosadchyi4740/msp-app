package com.romanosadchyi.smp.music_app.tabs.internal.service;

import com.romanosadchyi.smp.music_app.storage.external.FileStorage;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabResponse;
import com.romanosadchyi.smp.music_app.tabs.internal.dto.GuitarProTabUploadDto;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.GuitarProTab;
import com.romanosadchyi.smp.music_app.tabs.internal.mapping.TabMappingService;
import com.romanosadchyi.smp.music_app.tabs.internal.repository.TabRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.UUID;

/**
 * Uploads a Guitar Pro file as a new tab of the user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GuitarProTabUploadService {

    TabRepository tabRepository;
    TabValidator tabValidator;
    FileStorage fileStorage;
    TabMappingService tabMappingService;

    /**
     * Saves the tab first, so the database checks it before the file is stored; a failed upload rolls the row back.
     */
    @Transactional
    public TabResponse uploadGuitarPro(UUID ownerId, GuitarProTabUploadDto dto, MultipartFile file) {
        tabValidator.requireRightsConfirmed(dto.rightsConfirmed());
        String title = TabText.stripToNull(dto.title());
        String artist = TabText.stripToNull(dto.artist());
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
}
