package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.repository.MessageRepository;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.storage.external.FileStorage;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Files attached to chat messages (voice recordings). They live under {@link #KEY_PREFIX} in the file storage, and
 * a download URL is only given to users who may read a chat with a message carrying the file.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatAttachmentService {

    static final String KEY_PREFIX = "chat-attachments/";

    static final int MAX_NAME_LENGTH = 100;

    FileStorage fileStorage;
    MessageRepository messageRepository;
    UserLookup userLookup;

    /**
     * Stores the file and returns its key, which one message then carries as {@code fileUrl}.
     *
     * @throws UserNotFoundException if the user's account does not exist or was deleted
     */
    public String upload(MultipartFile file, UUID userId) {
        if (!userLookup.exists(userId)) {
            throw new UserNotFoundException("User not found: " + userId);
        }
        String key = KEY_PREFIX + UUID.randomUUID() + "-" + safeName(file.getOriginalFilename());
        fileStorage.upload(file, key);
        return key;
    }

    /**
     * @throws UserNotFoundException      if the user's account does not exist or was deleted
     * @throws UserNotAuthorizedException if no chat the user may read has a message with this file
     */
    @Transactional(readOnly = true)
    public String presignedUrl(String key, UUID userId) {
        if (!userLookup.exists(userId)) {
            throw new UserNotFoundException("User not found: " + userId);
        }
        boolean mayRead = isAttachmentKey(key) && messageRepository.findChatsWithAttachment(key).stream()
                .anyMatch(chat -> ChatMemberships.mayRead(chat, userId));
        if (!mayRead) {
            throw new UserNotAuthorizedException("You may not access this attachment");
        }
        return fileStorage.presignedGetUrl(key);
    }

    /**
     * The client's file name reduced to letters, digits, {@code .}, {@code _} and {@code -}, without {@code ..}, and
     * at most {@link #MAX_NAME_LENGTH} characters, so it cannot shape the key.
     */
    static String safeName(String originalName) {
        String name = originalName == null ? "" : originalName.replaceAll("[^A-Za-z0-9._-]", "_");
        while (name.contains("..")) {
            name = name.replace("..", ".");
        }
        if (name.isEmpty() || name.equals(".")) {
            name = "file";
        }
        return name.length() > MAX_NAME_LENGTH ? name.substring(name.length() - MAX_NAME_LENGTH) : name;
    }

    /**
     * Whether a message may carry this key: only chat attachments, never other stored files such as tracks.
     */
    static boolean isAttachmentKey(String key) {
        return key != null && key.startsWith(KEY_PREFIX) && !key.contains("..");
    }
}
