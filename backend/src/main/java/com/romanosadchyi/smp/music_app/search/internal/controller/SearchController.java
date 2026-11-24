package com.romanosadchyi.smp.music_app.search.internal.controller;

import com.romanosadchyi.smp.music_app.search.internal.dto.GlobalSearchResponseDto;
import com.romanosadchyi.smp.music_app.search.internal.service.SearchService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SearchController {

    SearchService searchService;

    @GetMapping
    public ResponseEntity<GlobalSearchResponseDto> search(@RequestParam("q") String query) {
        return ResponseEntity.ok(searchService.globalSearch(query, 6));
    }
}
