package com.romanosadchyi.smp.music_app.shared.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AfterCommitTest {

    List<String> ran = new ArrayList<>();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void run_OutsideATransaction_RunsAtOnce() {
        AfterCommit.run(() -> ran.add("work"));

        assertThat(ran).containsExactly("work");
    }

    @Test
    void run_InsideATransaction_WaitsForTheCommitAndKeepsTheOrder() {
        TransactionSynchronizationManager.initSynchronization();

        AfterCommit.run(() -> ran.add("first"));
        AfterCommit.run(() -> ran.add("second"));
        assertThat(ran).isEmpty();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(ran).containsExactly("first", "second");
    }

    @Test
    void run_RolledBack_NeverRuns() {
        TransactionSynchronizationManager.initSynchronization();

        AfterCommit.run(() -> ran.add("work"));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(ran).isEmpty();
    }
}
