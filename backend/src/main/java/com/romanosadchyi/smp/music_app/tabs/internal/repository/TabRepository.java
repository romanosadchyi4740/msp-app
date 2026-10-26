package com.romanosadchyi.smp.music_app.tabs.internal.repository;

import com.romanosadchyi.smp.music_app.tabs.internal.entity.Tab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TabRepository extends JpaRepository<Tab, UUID> {
    List<Tab> findAllByOwnerId(UUID ownerId);
}
