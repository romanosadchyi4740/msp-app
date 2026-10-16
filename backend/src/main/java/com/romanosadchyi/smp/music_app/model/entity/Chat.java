package com.romanosadchyi.smp.music_app.model.entity;

import com.romanosadchyi.smp.music_app.model.enums.ChatStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "chats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    UUID id;

    @Column(name = "is_group", nullable = false)
    @Builder.Default
    Boolean isGroup = false;

    @Column(name = "name")
    String name;

    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    Set<ChatMembership> memberships = new HashSet<>();

    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    Set<Message> messages = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;

    public Optional<ChatMembership> membershipOf(UUID userId) {
        return memberships.stream()
                .filter(m -> m.getUserId().equals(userId))
                .findFirst();
    }

    /**
     * Adds a membership for {@code userId} to this chat and returns it; the caller saves it.
     */
    public ChatMembership addMember(UUID userId, ChatStatus status, boolean admin) {
        ChatMembership membership = ChatMembership.builder()
                .chat(this)
                .userId(userId)
                .status(status)
                .isAdmin(admin)
                .build();
        memberships.add(membership);
        return membership;
    }
}
