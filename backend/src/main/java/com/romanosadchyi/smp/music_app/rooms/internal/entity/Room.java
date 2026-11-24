package com.romanosadchyi.smp.music_app.rooms.internal.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A room for live lessons. Its participants, the host among them, are {@link RoomParticipant}s.
 */
@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    UUID id;

    @Column(name = "host_id", nullable = false)
    UUID hostId;

    @Column(nullable = false, length = 100)
    String name;

    @Column(name = "invite_code", nullable = false, unique = true, length = 32)
    String inviteCode;

    /**
     * The most participants the room holds, the host included.
     */
    @Column(nullable = false)
    @Builder.Default
    int capacity = 5;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    RoomStatus status = RoomStatus.OPEN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
