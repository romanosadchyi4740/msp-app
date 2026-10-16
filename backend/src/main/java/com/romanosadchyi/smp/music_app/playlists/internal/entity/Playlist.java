package com.romanosadchyi.smp.music_app.playlists.internal.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "playlists", indexes = {
        @Index(name = "idx_playlists_title", columnList = "title"),
        @Index(name = "idx_playlists_owner_id", columnList = "owner_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Playlist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    UUID id;

    @Column(nullable = false)
    String title;

    @Column(columnDefinition = "TEXT")
    String description;

    @Column(name = "cover_url")
    String coverUrl;

    @Column(name = "owner_id", nullable = false)
    UUID ownerId;

    @Column(name = "is_public")
    @Builder.Default
    boolean isPublic = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forked_from_id")
    Playlist forkedFrom;

    /**
     * Ids of tracks in the music module, in playlist order; change it only through the list, so Hibernate keeps
     * {@code track_order} without gaps.
     */
    @ElementCollection
    @CollectionTable(name = "playlist_tracks", joinColumns = @JoinColumn(name = "playlist_id"))
    @Column(name = "track_id", nullable = false)
    @OrderColumn(name = "track_order")
    @Builder.Default
    List<UUID> trackIds = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;
}
