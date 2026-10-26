package com.romanosadchyi.smp.music_app.tabs.internal.mapping;

import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabFormat;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabPage;
import com.romanosadchyi.smp.music_app.tabs.internal.api.model.TabResponse;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.AsciiTab;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.GuitarProTab;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.Tab;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserRole;
import com.romanosadchyi.smp.music_app.users.external.UserSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TabMappingServiceTest {

    @Mock
    UserLookup userLookup;

    @InjectMocks
    TabMappingService mappingService;

    UUID ownerId = UUID.randomUUID();
    UserSummary owner = new UserSummary(ownerId, "Teacher", null, UserRole.ROLE_USER);

    @Test
    void map_AsciiTab_IncludesTheTextAndOwnerName() {
        AsciiTab tab = AsciiTab.builder().id(UUID.randomUUID()).ownerId(ownerId).title("Riff").isPublic(true)
                .rawText("e|---0---|").build();
        when(userLookup.findSummary(ownerId)).thenReturn(Optional.of(owner));

        TabResponse dto = mappingService.map(tab);

        assertThat(dto.getFormat()).isEqualTo(TabFormat.ASCII);
        assertThat(dto.getRawText()).isEqualTo("e|---0---|");
        assertThat(dto.getGpVersion()).isNull();
        assertThat(dto.getOwnerName()).isEqualTo("Teacher");
        assertThat(dto.getIsPublic()).isTrue();
    }

    @Test
    void map_GuitarProTab_IncludesTheVersionButNotTheFileKey() {
        GuitarProTab tab = GuitarProTab.builder().id(UUID.randomUUID()).ownerId(ownerId).title("Song")
                .fileKey("tabs/x.gp5").gpVersion("GP5").tempoBpm(100).timeSignature("3/4").build();
        when(userLookup.findSummary(ownerId)).thenReturn(Optional.empty());

        TabResponse dto = mappingService.map(tab);

        assertThat(dto.getFormat()).isEqualTo(TabFormat.GUITAR_PRO);
        assertThat(dto.getGpVersion()).isEqualTo("GP5");
        assertThat(dto.getRawText()).isNull();
        assertThat(dto.getTempoBpm()).isEqualTo(100);
        assertThat(dto.getTimeSignature()).isEqualTo("3/4");
        assertThat(dto.getOwnerName()).isNull();
    }

    @Test
    void mapAll_LooksUpOwnersOnceAndLeavesOutText() {
        UUID otherId = UUID.randomUUID();
        List<Tab> tabs = List.of(
                AsciiTab.builder().ownerId(ownerId).title("A").rawText("e|---|").build(),
                AsciiTab.builder().ownerId(ownerId).title("B").rawText("e|---|").build(),
                GuitarProTab.builder().ownerId(otherId).title("C").fileKey("tabs/c.gp").build());
        when(userLookup.findSummaries(List.of(ownerId, otherId))).thenReturn(Map.of(ownerId, owner));

        List<TabResponse> dtos = mappingService.mapAll(tabs);

        assertThat(dtos).extracting(TabResponse::getOwnerName).containsExactly("Teacher", "Teacher", null);
        assertThat(dtos).extracting(TabResponse::getRawText).containsOnlyNulls();
        verify(userLookup, never()).findSummary(any());
    }

    @Test
    void mapPage_LooksUpOwnersOnce() {
        Page<Tab> tabs = new PageImpl<>(List.of(AsciiTab.builder().ownerId(ownerId).title("A").rawText("x").build()));
        when(userLookup.findSummaries(List.of(ownerId))).thenReturn(Map.of(ownerId, owner));

        TabPage page = mappingService.mapPage(tabs);

        assertThat(page.getContent()).singleElement()
                .satisfies(dto -> assertThat(dto.getOwnerName()).isEqualTo("Teacher"))
                .satisfies(dto -> assertThat(dto.getRawText()).isNull());
        assertThat(page.getNumber()).isZero();
        assertThat(page.getSize()).isEqualTo(1);
        assertThat(page.getNumberOfElements()).isEqualTo(1);
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getTotalPages()).isEqualTo(1);
        assertThat(page.getFirst()).isTrue();
        assertThat(page.getLast()).isTrue();
        assertThat(page.getEmpty()).isFalse();
        verify(userLookup, never()).findSummary(any());
    }
}
