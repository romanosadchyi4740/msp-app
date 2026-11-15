package com.romanosadchyi.smp.music_app.rooms.internal.entity;

import com.romanosadchyi.smp.music_app.shared.util.EncryptedTextConverter;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A message of a room's text chat (KAN-51; lab 7 ChatMessage). The content is stored encrypted.
 */
@Entity
@Table(name = "room_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    Room room;

    /**
     * The user who wrote it; their messages stay in the room after they leave.
     */
    @Column(name = "author_id", nullable = false)
    UUID authorId;

    @Convert(converter = EncryptedTextConverter.class)
    @Column(columnDefinition = "TEXT", nullable = false)
    String content;

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    Instant sentAt;
}
