package com.romanosadchyi.smp.music_app.telemetry.internal.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "status_broadcasts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StatusBroadcast {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "author_id", nullable = false)
    UUID authorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    String content;

    /**
     * Id of a track in the music module.
     */
    @Column(name = "attached_track_id")
    UUID attachedTrackId;

    @Column(nullable = false)
    Instant timestamp;

    @OneToOne(mappedBy = "broadcast", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    ConsensusPoll poll;
}
