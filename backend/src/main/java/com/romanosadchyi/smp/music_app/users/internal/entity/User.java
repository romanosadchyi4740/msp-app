package com.romanosadchyi.smp.music_app.users.internal.entity;

import com.romanosadchyi.smp.music_app.users.external.UserRole;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_display_name", columnList = "display_name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    UUID id;

    @Column(nullable = false, unique = true)
    String email;

    @Column(name = "password")
    String password;

    @Column(name = "display_name")
    String displayName;

    @Column(name = "avatar_url")
    String avatarUrl;

    @Column(columnDefinition = "TEXT")
    String biography;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_followers",
            joinColumns = @JoinColumn(name = "follower_id"),
            inverseJoinColumns = @JoinColumn(name = "following_id")
    )
    @Builder.Default
    Set<User> following = new HashSet<>();

    @ManyToMany(mappedBy = "following", fetch = FetchType.LAZY)
    @Builder.Default
    Set<User> followers = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider")
    AuthProvider provider;

    @Column(name = "provider_id")
    String providerId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;
}
