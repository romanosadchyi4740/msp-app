package com.romanosadchyi.smp.music_app.telemetry.internal.repository;

import com.romanosadchyi.smp.music_app.telemetry.internal.entity.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PollVoteRepository extends JpaRepository<PollVote, UUID> {

    @Query("SELECT COUNT(v) FROM PollVote v WHERE v.option.poll.id = :pollId AND v.voterId = :voterId")
    long countByPollIdAndVoterId(@Param("pollId") UUID pollId, @Param("voterId") UUID voterId);

    @Query("SELECT v FROM PollVote v WHERE v.option.poll.id = :pollId AND v.voterId = :voterId")
    Optional<PollVote> findByPollIdAndVoterId(@Param("pollId") UUID pollId, @Param("voterId") UUID voterId);

    List<PollVote> findAllByVoterId(UUID voterId);
}
