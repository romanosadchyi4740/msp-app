package com.romanosadchyi.smp.music_app.users.internal.service;

import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import com.romanosadchyi.smp.music_app.users.external.UserResponseDto;
import com.romanosadchyi.smp.music_app.users.external.UserRole;
import com.romanosadchyi.smp.music_app.users.external.UserSummary;
import com.romanosadchyi.smp.music_app.users.internal.entity.User;
import com.romanosadchyi.smp.music_app.users.internal.mapping.UserMappingService;
import com.romanosadchyi.smp.music_app.users.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLookupServiceTest {

    @Mock
    UserRepository userRepository;

    UserLookupService userLookup;

    final User user = User.builder()
            .id(UUID.randomUUID())
            .email("artist@example.com")
            .displayName("Artist")
            .avatarUrl("avatar.png")
            .role(UserRole.ROLE_ARTIST)
            .build();

    @BeforeEach
    void setUp() {
        userLookup = new UserLookupService(userRepository, new UserMappingService());
    }

    @Test
    void getSummary_ExistingUser_ReturnsSummary() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThat(userLookup.getSummary(user.getId()))
                .isEqualTo(new UserSummary(user.getId(), "Artist", "avatar.png", UserRole.ROLE_ARTIST));
    }

    @Test
    void getSummary_UnknownUser_ThrowsUserNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userLookup.getSummary(id)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void findSummary_UnknownUser_IsEmpty() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(userLookup.findSummary(id)).isEmpty();
    }

    @Test
    void findSummaries_ReturnsOnlyExistingUsersById() {
        UUID unknown = UUID.randomUUID();
        when(userRepository.findAllById(List.of(user.getId(), unknown))).thenReturn(List.of(user));

        Map<UUID, UserSummary> summaries = userLookup.findSummaries(List.of(user.getId(), unknown));

        assertThat(summaries).containsOnlyKeys(user.getId());
        assertThat(summaries.get(user.getId()).displayName()).isEqualTo("Artist");
    }

    @Test
    void getProfile_ReturnsPublicProfile() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UserResponseDto profile = userLookup.getProfile(user.getId());

        assertThat(profile.id()).isEqualTo(user.getId());
        assertThat(profile.email()).isEqualTo("artist@example.com");
        assertThat(profile.role()).isEqualTo("ROLE_ARTIST");
        assertThat(profile.followersCount()).isZero();
    }

    @Test
    void searchProfiles_MapsMatchingUsers() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findAllByDisplayNameContainingIgnoreCaseAndDeletedAtIsNull("art", pageable))
                .thenReturn(new PageImpl<>(List.of(user)));

        assertThat(userLookup.searchProfiles("art", pageable).getContent())
                .extracting(UserResponseDto::displayName)
                .containsExactly("Artist");
    }

    @Test
    void existsAndFollowingIds_DelegateToRepository() {
        UUID followed = UUID.randomUUID();
        when(userRepository.existsByIdAndDeletedAtIsNull(user.getId())).thenReturn(true);
        when(userRepository.findFollowingIds(user.getId())).thenReturn(Set.of(followed));

        assertThat(userLookup.exists(user.getId())).isTrue();
        assertThat(userLookup.findFollowingIds(user.getId())).containsExactly(followed);
    }

    @Test
    void findDeletedIds_DelegatesToRepository() {
        UUID deleted = UUID.randomUUID();
        List<UUID> ids = List.of(user.getId(), deleted);
        when(userRepository.findDeletedIds(ids)).thenReturn(Set.of(deleted));

        assertThat(userLookup.findDeletedIds(ids)).containsExactly(deleted);
    }

    @Test
    void findDeletedIds_NoIds_DoesNotQuery() {
        assertThat(userLookup.findDeletedIds(List.of())).isEmpty();
        verifyNoInteractions(userRepository);
    }
}
