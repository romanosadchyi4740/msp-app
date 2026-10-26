package com.romanosadchyi.smp.music_app.tabs.internal.mapping;

import com.romanosadchyi.smp.music_app.tabs.internal.dto.TabFormat;
import com.romanosadchyi.smp.music_app.tabs.internal.dto.TabResponseDto;
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

        TabResponseDto dto = mappingService.map(tab);

        assertThat(dto.format()).isEqualTo(TabFormat.ASCII);
        assertThat(dto.rawText()).isEqualTo("e|---0---|");
        assertThat(dto.gpVersion()).isNull();
        assertThat(dto.ownerName()).isEqualTo("Teacher");
        assertThat(dto.isPublic()).isTrue();
    }

    @Test
    void map_GuitarProTab_IncludesTheVersionButNotTheFileKey() {
        GuitarProTab tab = GuitarProTab.builder().id(UUID.randomUUID()).ownerId(ownerId).title("Song")
                .fileKey("tabs/x.gp5").gpVersion("GP5").tempoBpm(100).timeSignature("3/4").build();
        when(userLookup.findSummary(ownerId)).thenReturn(Optional.empty());

        TabResponseDto dto = mappingService.map(tab);

        assertThat(dto.format()).isEqualTo(TabFormat.GUITAR_PRO);
        assertThat(dto.gpVersion()).isEqualTo("GP5");
        assertThat(dto.rawText()).isNull();
        assertThat(dto.tempoBpm()).isEqualTo(100);
        assertThat(dto.timeSignature()).isEqualTo("3/4");
        assertThat(dto.ownerName()).isNull();
    }

    @Test
    void mapAll_LooksUpOwnersOnceAndLeavesOutText() {
        UUID otherId = UUID.randomUUID();
        List<Tab> tabs = List.of(
                AsciiTab.builder().ownerId(ownerId).title("A").rawText("e|---|").build(),
                AsciiTab.builder().ownerId(ownerId).title("B").rawText("e|---|").build(),
                GuitarProTab.builder().ownerId(otherId).title("C").fileKey("tabs/c.gp").build());
        when(userLookup.findSummaries(List.of(ownerId, otherId))).thenReturn(Map.of(ownerId, owner));

        List<TabResponseDto> dtos = mappingService.mapAll(tabs);

        assertThat(dtos).extracting(TabResponseDto::ownerName).containsExactly("Teacher", "Teacher", null);
        assertThat(dtos).extracting(TabResponseDto::rawText).containsOnlyNulls();
        verify(userLookup, never()).findSummary(any());
    }

    @Test
    void mapPage_LooksUpOwnersOnce() {
        Page<Tab> tabs = new PageImpl<>(List.of(AsciiTab.builder().ownerId(ownerId).title("A").rawText("x").build()));
        when(userLookup.findSummaries(List.of(ownerId))).thenReturn(Map.of(ownerId, owner));

        Page<TabResponseDto> page = mappingService.mapPage(tabs);

        assertThat(page.getContent()).singleElement()
                .satisfies(dto -> assertThat(dto.ownerName()).isEqualTo("Teacher"))
                .satisfies(dto -> assertThat(dto.rawText()).isNull());
        verify(userLookup, never()).findSummary(any());
    }
}
