package com.romanosadchyi.smp.music_app.rooms.internal.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_participants", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"room_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    Room room;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    RoomRole role;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    Instant joinedAt;

    /**
     * The last measured round-trip time to the other participants, from WebRTC statistics.
     */
    @Column(name = "rtt_ms")
    Integer rttMs;

    @Column(nullable = false)
    @Builder.Default
    boolean online = false;
}
