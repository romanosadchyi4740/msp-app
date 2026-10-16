package com.romanosadchyi.smp.music_app.model.entity;

import com.romanosadchyi.smp.music_app.model.enums.ChatStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_memberships", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"chat_id", "user_id"})
}, indexes = {
        @Index(name = "idx_chat_memberships_user_status", columnList = "user_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    Chat chat;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    ChatStatus status = ChatStatus.PENDING;

    @Column(name = "is_admin", nullable = false)
    @Builder.Default
    boolean isAdmin = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;
}
