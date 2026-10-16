package com.romanosadchyi.smp.music_app.telemetry.internal.repository;

import com.romanosadchyi.smp.music_app.telemetry.internal.entity.PollOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PollOptionRepository extends JpaRepository<PollOption, UUID> {
}
