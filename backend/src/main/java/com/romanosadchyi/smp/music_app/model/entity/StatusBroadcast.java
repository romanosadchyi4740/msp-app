package com.romanosadchyi.smp.music_app.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "status_broadcasts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusBroadcast {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attached_track_id")
    private Track attachedTrack;

    @Column(nullable = false)
    private Instant timestamp;

    @OneToOne(mappedBy = "broadcast", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private ConsensusPoll poll;
}
