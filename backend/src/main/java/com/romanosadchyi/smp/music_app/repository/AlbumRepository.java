package com.romanosadchyi.smp.music_app.repository;

import com.romanosadchyi.smp.music_app.model.entity.Album;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlbumRepository extends JpaRepository<Album, UUID> {
    List<Album> findAllByAuthorId(UUID artistId);
    Page<Album> findAllByTitleContainingIgnoreCase(String title, Pageable pageable);
    Page<Album> findAllByAuthorIdAndTitleContainingIgnoreCase(UUID authorId, String title, Pageable pageable);
}
