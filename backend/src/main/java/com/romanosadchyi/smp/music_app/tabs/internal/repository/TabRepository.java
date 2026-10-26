package com.romanosadchyi.smp.music_app.tabs.internal.repository;

import com.romanosadchyi.smp.music_app.tabs.internal.entity.Tab;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TabRepository extends JpaRepository<Tab, UUID> {
    List<Tab> findAllByOwnerId(UUID ownerId);

    List<Tab> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    /**
     * Public, not hidden tabs whose title or artist contains {@code query}, ignoring case. {@code query} has
     * {@code \}, {@code %} and {@code _} escaped with a backslash.
     */
    @Query("""
            SELECT t FROM Tab t
            WHERE t.isPublic = TRUE AND t.hidden = FALSE
              AND (LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\'
                OR LOWER(t.artist) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\')
            """)
    Page<Tab> searchPublic(@Param("query") String query, Pageable pageable);
}
