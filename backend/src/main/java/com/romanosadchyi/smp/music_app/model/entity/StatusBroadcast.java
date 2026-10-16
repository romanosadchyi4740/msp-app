package com.romanosadchyi.smp.music_app.model.entity;

import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attached_track_id")
    Track attachedTrack;

    @Column(nullable = false)
    Instant timestamp;

    @OneToOne(mappedBy = "broadcast", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    ConsensusPoll poll;
}
