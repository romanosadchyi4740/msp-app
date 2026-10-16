package com.romanosadchyi.smp.music_app.model.entity;

import com.romanosadchyi.smp.music_app.model.enums.RequestStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "integration_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IntegrationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_playlist_id", nullable = false)
    Playlist targetPlaylist;

    @Column(name = "requester_id", nullable = false)
    UUID requesterId;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "integration_request_tracks",
            joinColumns = @JoinColumn(name = "request_id"),
            inverseJoinColumns = @JoinColumn(name = "track_id")
    )
    @Builder.Default
    Set<Track> proposedTracks = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    RequestStatus status = RequestStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;
}
