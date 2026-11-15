package com.romanosadchyi.smp.music_app.rooms.internal.mapping;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomMessagePage;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomMessageResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomMessage;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserRole;
import com.romanosadchyi.smp.music_app.users.external.UserSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomChatMappingServiceTest {

    @Mock
    UserLookup userLookup;

    @InjectMocks
    RoomChatMappingService mappingService;

    final Room room = Room.builder().id(UUID.randomUUID()).build();
    final UUID alice = UUID.randomUUID();
    final UUID bob = UUID.randomUUID();

    @Test
    void map_KnownAuthor_FillsNameAndAvatar() {
        RoomMessage message = message(alice, "hi");
        when(userLookup.findSummary(alice)).thenReturn(Optional.of(summary(alice, "Alice")));

        RoomMessageResponse response = mappingService.map(message);

        assertThat(response.getId()).isEqualTo(message.getId());
        assertThat(response.getRoomId()).isEqualTo(room.getId());
        assertThat(response.getAuthorId()).isEqualTo(alice);
        assertThat(response.getAuthorName()).isEqualTo("Alice");
        assertThat(response.getAuthorAvatarUrl()).isEqualTo("Alice.png");
        assertThat(response.getContent()).isEqualTo("hi");
        assertThat(response.getSentAt()).isEqualTo(message.getSentAt());
    }

    @Test
    void map_UnknownAuthor_LeavesTheNameOut() {
        when(userLookup.findSummary(alice)).thenReturn(Optional.empty());

        RoomMessageResponse response = mappingService.map(message(alice, "hi"));

        assertThat(response.getAuthorId()).isEqualTo(alice);
        assertThat(response.getAuthorName()).isNull();
        assertThat(response.getAuthorAvatarUrl()).isNull();
    }

    @Test
    void mapPage_ResolvesAuthorsInOneCall() {
        List<RoomMessage> messages = List.of(message(alice, "3"), message(bob, "2"), message(alice, "1"));
        when(userLookup.findSummaries(List.of(alice, bob)))
                .thenReturn(Map.of(alice, summary(alice, "Alice"), bob, summary(bob, "Bob")));

        RoomMessagePage page = mappingService.mapPage(new PageImpl<>(messages, PageRequest.of(1, 3), 7));

        verify(userLookup).findSummaries(List.of(alice, bob));
        assertThat(page.getContent()).extracting(RoomMessageResponse::getAuthorName)
                .containsExactly("Alice", "Bob", "Alice");
        assertThat(page.getContent()).extracting(RoomMessageResponse::getContent).containsExactly("3", "2", "1");
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(3);
        assertThat(page.getNumberOfElements()).isEqualTo(3);
        assertThat(page.getTotalElements()).isEqualTo(7);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getFirst()).isFalse();
        assertThat(page.getLast()).isFalse();
        assertThat(page.getEmpty()).isFalse();
    }

    private RoomMessage message(UUID authorId, String content) {
        return RoomMessage.builder()
                .id(UUID.randomUUID())
                .room(room)
                .authorId(authorId)
                .content(content)
                .sentAt(Instant.parse("2026-11-15T20:00:00Z"))
                .build();
    }

    private static UserSummary summary(UUID id, String name) {
        return new UserSummary(id, name, name + ".png", UserRole.ROLE_USER);
    }
}
