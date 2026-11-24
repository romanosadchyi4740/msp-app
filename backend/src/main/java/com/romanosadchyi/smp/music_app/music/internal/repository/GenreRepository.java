package com.romanosadchyi.smp.music_app.music.internal.repository;

import com.romanosadchyi.smp.music_app.music.internal.entity.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GenreRepository extends JpaRepository<Genre, UUID> {
}
