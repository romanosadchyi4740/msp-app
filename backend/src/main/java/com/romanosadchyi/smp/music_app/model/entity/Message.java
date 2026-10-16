package com.romanosadchyi.smp.music_app.model.entity;

import com.romanosadchyi.smp.music_app.model.converter.MessageEncryptionConverter;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_messages_chat_id_timestamp", columnList = "chat_id, timestamp DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    Chat chat;

    @Column(name = "sender_id", nullable = false)
    UUID senderId;

    @Convert(converter = MessageEncryptionConverter.class)
    @Column(columnDefinition = "TEXT", nullable = false)
    String content;

    @Column(name = "file_url", length = 1024)
    String fileUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_track_id")
    Track sharedTrack;

    @Column(name = "shared_track_timestamp")
    Integer sharedTrackTimestamp;

    @CreationTimestamp
    @Column(name = "timestamp", updatable = false)
    LocalDateTime timestamp;
}
