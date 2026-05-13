package com.romanosadchyi.smp.music_app.repository;

import com.romanosadchyi.smp.music_app.model.entity.IntegrationRequest;
import com.romanosadchyi.smp.music_app.model.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IntegrationRequestRepository extends JpaRepository<IntegrationRequest, UUID> {
    List<IntegrationRequest> findAllByTargetPlaylistIdAndStatusOrderByCreatedAtDesc(UUID targetPlaylistId, RequestStatus status);
}