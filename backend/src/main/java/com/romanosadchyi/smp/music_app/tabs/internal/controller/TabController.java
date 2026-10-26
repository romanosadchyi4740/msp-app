package com.romanosadchyi.smp.music_app.tabs.internal.controller;

import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
import com.romanosadchyi.smp.music_app.tabs.internal.dto.AsciiTabCreateDto;
import com.romanosadchyi.smp.music_app.tabs.internal.dto.GuitarProTabUploadDto;
import com.romanosadchyi.smp.music_app.tabs.internal.dto.TabResponseDto;
import com.romanosadchyi.smp.music_app.tabs.internal.dto.TabUpdateDto;
import com.romanosadchyi.smp.music_app.tabs.internal.service.TabService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/tabs")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TabController {

    TabService tabService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TabResponseDto uploadGuitarPro(@AuthenticationPrincipal AuthenticatedUser user,
                                          @ModelAttribute GuitarProTabUploadDto dto,
                                          @RequestParam("file") MultipartFile file) {
        log.info("Uploading Guitar Pro tab for user: {}", user.id());
        return tabService.uploadGuitarPro(user.id(), dto, file);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TabResponseDto createAscii(@AuthenticationPrincipal AuthenticatedUser user,
                                      @RequestBody AsciiTabCreateDto dto) {
        log.info("Creating ASCII tab for user: {}", user.id());
        return tabService.createAscii(user.id(), dto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TabResponseDto> update(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @PathVariable UUID id,
                                                 @RequestBody TabUpdateDto dto) {
        log.info("User {} updating tab {}", user.id(), id);
        return ResponseEntity.ok(tabService.update(user.id(), id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        log.info("User {} deleting tab {}", user.id(), id);
        tabService.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my")
    public ResponseEntity<List<TabResponseDto>> getMyTabs(@AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Getting tabs of user: {}", user.id());
        return ResponseEntity.ok(tabService.getMyTabs(user.id()));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<TabResponseDto>> search(
            @RequestParam(value = "q", defaultValue = "", required = false) String query,
            @RequestParam(value = "pageNo", defaultValue = "0", required = false) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20", required = false) int pageSize
    ) {
        log.info("Searching tabs with query: {}", query);
        return ResponseEntity.ok(tabService.search(query, pageNo, pageSize));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TabResponseDto> getTab(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @PathVariable UUID id) {
        log.info("User {} getting tab {}", user.id(), id);
        return ResponseEntity.ok(tabService.getTab(user.id(), id));
    }

    @GetMapping("/{id}/url")
    public ResponseEntity<String> getFileUrl(@AuthenticationPrincipal AuthenticatedUser user,
                                             @PathVariable UUID id) {
        log.info("User {} getting the file URL of tab {}", user.id(), id);
        return ResponseEntity.ok(tabService.getFileUrl(user.id(), id));
    }
}
