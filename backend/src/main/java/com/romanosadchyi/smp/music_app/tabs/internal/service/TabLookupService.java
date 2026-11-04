package com.romanosadchyi.smp.music_app.tabs.internal.service;

import com.romanosadchyi.smp.music_app.tabs.external.TabLookup;
import com.romanosadchyi.smp.music_app.tabs.external.TabSummary;
import com.romanosadchyi.smp.music_app.tabs.internal.repository.TabRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class TabLookupService implements TabLookup {

    TabRepository tabRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<TabSummary> findReadable(UUID tabId, UUID userId) {
        return tabRepository.findById(tabId)
                .filter(tab -> TabVisibility.isReadableBy(tab, userId))
                .map(tab -> new TabSummary(tab.getId(), tab.getTitle(), tab.getTempoBpm(), tab.getTimeSignature()));
    }
}
