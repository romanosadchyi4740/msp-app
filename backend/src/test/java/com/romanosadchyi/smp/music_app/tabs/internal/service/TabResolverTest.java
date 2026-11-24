package com.romanosadchyi.smp.music_app.tabs.internal.service;

import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.tabs.external.TabReadGrant;
import com.romanosadchyi.smp.music_app.tabs.internal.entity.AsciiTab;
import com.romanosadchyi.smp.music_app.tabs.internal.exception.TabNotFoundException;
import com.romanosadchyi.smp.music_app.tabs.internal.repository.TabRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TabResolverTest {

    @Mock
    TabRepository tabRepository;

    @Mock
    TabReadGrant firstGrant;

    @Mock
    TabReadGrant secondGrant;

    TabResolver tabResolver;

    UUID ownerId = UUID.randomUUID();
    UUID otherId = UUID.randomUUID();
    UUID tabId = UUID.randomUUID();

    @BeforeEach
    void createTheResolver() {
        tabResolver = new TabResolver(tabRepository, List.of(firstGrant, secondGrant));
    }

    @Test
    void getOwnTab_OwnTab_IsReturned() {
        AsciiTab tab = tab(false, false);
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(tab));

        assertThat(tabResolver.getOwnTab(ownerId, tabId)).isSameAs(tab);
    }

    @Test
    void getOwnTab_SomeoneElsesPublicTab_IsForbidden() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(tab(true, false)));

        assertThatThrownBy(() -> tabResolver.getOwnTab(otherId, tabId))
                .isInstanceOf(UserNotAuthorizedException.class)
                .hasMessage("You can only change your own tabs");
    }

    @Test
    void getOwnTab_MissingTab_IsNotFound() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tabResolver.getOwnTab(ownerId, tabId))
                .isInstanceOf(TabNotFoundException.class)
                .hasMessage("Tab not found");
    }

    @Test
    void getReadableTab_MissingTab_IsNotFound() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tabResolver.getReadableTab(ownerId, tabId))
                .isInstanceOf(TabNotFoundException.class)
                .hasMessage("Tab not found");
        verifyNoInteractions(firstGrant, secondGrant);
    }

    @Test
    void getReadableTab_OthersPrivateTab_IsReturnedWhenAnyGrantAllowsIt() {
        AsciiTab tab = tab(false, false);
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(tab));
        when(firstGrant.allowsRead(tabId, otherId)).thenReturn(false);
        when(secondGrant.allowsRead(tabId, otherId)).thenReturn(true);

        assertThat(tabResolver.getReadableTab(otherId, tabId)).isSameAs(tab);
    }

    @Test
    void getReadableTab_OthersPrivateTab_IsNotFoundWhenNoGrantAllowsIt() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(tab(false, false)));

        assertThatThrownBy(() -> tabResolver.getReadableTab(otherId, tabId))
                .isInstanceOf(TabNotFoundException.class);
        verify(firstGrant).allowsRead(tabId, otherId);
        verify(secondGrant).allowsRead(tabId, otherId);
    }

    @Test
    void getReadableTab_OthersHiddenPublicTab_IsNotFoundWithoutAskingTheGrants() {
        when(tabRepository.findById(tabId)).thenReturn(Optional.of(tab(true, true)));

        assertThatThrownBy(() -> tabResolver.getReadableTab(otherId, tabId))
                .isInstanceOf(TabNotFoundException.class);
        verifyNoInteractions(firstGrant, secondGrant);
    }

    private AsciiTab tab(boolean isPublic, boolean hidden) {
        return AsciiTab.builder().id(tabId).ownerId(ownerId).title("Riff").isPublic(isPublic).hidden(hidden)
                .rightsConfirmed(true).rawText("e|---0---|").build();
    }
}
