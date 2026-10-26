package com.romanosadchyi.smp.music_app.tabs.internal.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A tab in one of the supported formats; the subclass holds the format's content. All formats share the
 * {@code tabs} table, told apart by {@code format}.
 */
@Entity
@Table(name = "tabs")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "format", discriminatorType = DiscriminatorType.STRING, length = 20)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public abstract class Tab {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    UUID id;

    @Column(name = "owner_id", nullable = false)
    UUID ownerId;

    @Column(nullable = false)
    String title;

    String artist;

    @Column(name = "tempo_bpm")
    Integer tempoBpm;

    /**
     * Such as {@code 4/4} or {@code 6/8}.
     */
    @Column(name = "time_signature")
    String timeSignature;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    boolean isPublic = false;

    /**
     * The owner confirmed the material is theirs or in the public domain (R14); the database rejects {@code false}.
     */
    @Column(name = "rights_confirmed", nullable = false)
    boolean rightsConfirmed;

    /**
     * Set by moderation when a complaint is accepted.
     */
    @Column(nullable = false)
    @Builder.Default
    boolean hidden = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
