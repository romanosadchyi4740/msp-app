package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.entity.Chat;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatStatus;
import com.romanosadchyi.smp.music_app.chats.internal.repository.MessageRepository;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.storage.external.FileStorage;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAttachmentServiceTest {

    @Mock
    FileStorage fileStorage;

    @Mock
    MessageRepository messageRepository;

    @Mock
    UserLookup userLookup;

    @InjectMocks
    ChatAttachmentService chatAttachmentService;

    final UUID userId = UUID.randomUUID();

    final String key = "chat-attachments/" + UUID.randomUUID() + "-voice.webm";

    @Test
    void upload_StoresTheFileUnderTheAttachmentPrefix() {
        MockMultipartFile file = new MockMultipartFile("file", "voice.webm", "audio/webm", new byte[]{1});
        when(userLookup.exists(userId)).thenReturn(true);

        String stored = chatAttachmentService.upload(file, userId);

        assertThat(stored).startsWith("chat-attachments/").endsWith("-voice.webm");
        assertThat(ChatAttachmentService.isAttachmentKey(stored)).isTrue();
        verify(fileStorage).upload(file, stored);
    }

    @Test
    void upload_DeletedAccount_IsNotFound() {
        MockMultipartFile file = new MockMultipartFile("file", "voice.webm", "audio/webm", new byte[]{1});
        when(userLookup.exists(userId)).thenReturn(false);

        assertThatThrownBy(() -> chatAttachmentService.upload(file, userId)).isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(fileStorage);
    }

    @Test
    void safeName_KeepsOnlyHarmlessCharacters() {
        assertThat(ChatAttachmentService.safeName("take..2.webm")).isEqualTo("take.2.webm");
        assertThat(ChatAttachmentService.safeName("../../tracks/x y.mp3")).isEqualTo("._._tracks_x_y.mp3");
        assertThat(ChatAttachmentService.safeName(null)).isEqualTo("file");
        assertThat(ChatAttachmentService.safeName("..")).isEqualTo("file");
        assertThat(ChatAttachmentService.safeName("a".repeat(300) + ".webm")).hasSize(100).endsWith(".webm");
        assertThat(ChatAttachmentService.isAttachmentKey("chat-attachments/x-" + ChatAttachmentService.safeName("a..b")))
                .isTrue();
    }

    @Test
    void presignedUrl_MemberOfAChatWithTheFile_GetsTheUrl() {
        Chat chat = Chat.builder().id(UUID.randomUUID()).build();
        ChatMemberships.add(chat, userId, ChatStatus.ACCEPTED, false);
        when(userLookup.exists(userId)).thenReturn(true);
        when(messageRepository.findChatsWithAttachment(key)).thenReturn(List.of(chat));
        when(fileStorage.presignedGetUrl(key)).thenReturn("https://storage/signed");

        assertThat(chatAttachmentService.presignedUrl(key, userId)).isEqualTo("https://storage/signed");
    }

    @Test
    void presignedUrl_NotAMemberOfAnyChatWithTheFile_IsForbidden() {
        Chat chat = Chat.builder().id(UUID.randomUUID()).build();
        ChatMemberships.add(chat, UUID.randomUUID(), ChatStatus.ACCEPTED, true);
        when(userLookup.exists(userId)).thenReturn(true);
        when(messageRepository.findChatsWithAttachment(key)).thenReturn(List.of(chat));

        assertThatThrownBy(() -> chatAttachmentService.presignedUrl(key, userId))
                .isInstanceOf(UserNotAuthorizedException.class);
        verify(fileStorage, never()).presignedGetUrl(anyString());
    }

    @Test
    void presignedUrl_DeclinedGroupWithTheFile_IsForbidden() {
        Chat group = Chat.builder().id(UUID.randomUUID()).isGroup(true).build();
        ChatMemberships.add(group, userId, ChatStatus.REJECTED, false);
        when(userLookup.exists(userId)).thenReturn(true);
        when(messageRepository.findChatsWithAttachment(key)).thenReturn(List.of(group));

        assertThatThrownBy(() -> chatAttachmentService.presignedUrl(key, userId))
                .isInstanceOf(UserNotAuthorizedException.class);
        verify(fileStorage, never()).presignedGetUrl(anyString());
    }

    @Test
    void presignedUrl_FileOnSeveralChats_IsAllowedIfOneIsReadable() {
        Chat unreadable = Chat.builder().id(UUID.randomUUID()).isGroup(true).build();
        ChatMemberships.add(unreadable, userId, ChatStatus.REJECTED, false);
        Chat readable = Chat.builder().id(UUID.randomUUID()).build();
        ChatMemberships.add(readable, userId, ChatStatus.PENDING, false);
        when(userLookup.exists(userId)).thenReturn(true);
        when(messageRepository.findChatsWithAttachment(key)).thenReturn(List.of(unreadable, readable));
        when(fileStorage.presignedGetUrl(key)).thenReturn("https://storage/signed");

        assertThat(chatAttachmentService.presignedUrl(key, userId)).isEqualTo("https://storage/signed");
    }

    @Test
    void presignedUrl_FileOnNoMessage_IsForbidden() {
        when(userLookup.exists(userId)).thenReturn(true);
        when(messageRepository.findChatsWithAttachment(key)).thenReturn(List.of());

        assertThatThrownBy(() -> chatAttachmentService.presignedUrl(key, userId))
                .isInstanceOf(UserNotAuthorizedException.class);
        verify(fileStorage, never()).presignedGetUrl(anyString());
    }

    @Test
    void presignedUrl_KeyOutsideTheAttachments_IsForbiddenWithoutLookingItUp() {
        when(userLookup.exists(userId)).thenReturn(true);

        assertThatThrownBy(() -> chatAttachmentService.presignedUrl("tracks/song.mp3", userId))
                .isInstanceOf(UserNotAuthorizedException.class);
        assertThatThrownBy(() -> chatAttachmentService.presignedUrl("chat-attachments/../tracks/song.mp3", userId))
                .isInstanceOf(UserNotAuthorizedException.class);
        verifyNoInteractions(messageRepository, fileStorage);
    }

    @Test
    void presignedUrl_DeletedAccount_IsNotFound() {
        when(userLookup.exists(userId)).thenReturn(false);

        assertThatThrownBy(() -> chatAttachmentService.presignedUrl(key, userId))
                .isInstanceOf(UserNotFoundException.class);
        verify(messageRepository, never()).findChatsWithAttachment(any());
        verifyNoInteractions(fileStorage);
    }
}
