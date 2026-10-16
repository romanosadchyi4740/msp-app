package com.romanosadchyi.smp.music_app.telemetry.internal.service;

import com.romanosadchyi.smp.music_app.telemetry.internal.entity.PollVote;
import com.romanosadchyi.smp.music_app.telemetry.internal.repository.PollVoteRepository;
import com.romanosadchyi.smp.music_app.telemetry.internal.repository.StatusBroadcastRepository;
import com.romanosadchyi.smp.music_app.users.external.UserDataCleanup;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * On account deletion: deletes the user's votes on other users' polls, then the user's status broadcasts with
 * their polls, options and votes.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class TelemetryUserDataCleanup implements UserDataCleanup {

    PollVoteRepository pollVoteRepository;
    StatusBroadcastRepository broadcastRepository;

    @Override
    public void deleteUserData(UUID userId) {
        // Through the option's collection, which owns the votes (orphan removal)
        for (PollVote vote : pollVoteRepository.findAllByVoterId(userId)) {
            vote.getOption().getVotes().remove(vote);
        }
        broadcastRepository.deleteAll(broadcastRepository.findAllByAuthorId(userId));
    }
}
