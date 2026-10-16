package com.romanosadchyi.smp.music_app.users.internal.repository;

import com.romanosadchyi.smp.music_app.users.internal.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Page<User> findAllByDisplayNameContainingIgnoreCaseAndDeletedAtIsNull(String displayName, Pageable pageable);
    boolean existsByEmail(String email);
    boolean existsByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT f.id FROM User u JOIN u.following f WHERE u.id = :userId")
    Set<UUID> findFollowingIds(@Param("userId") UUID userId);

    @Query("SELECT u.id FROM User u WHERE u.id IN :userIds AND u.deletedAt IS NOT NULL")
    Set<UUID> findDeletedIds(@Param("userIds") Collection<UUID> userIds);
}
