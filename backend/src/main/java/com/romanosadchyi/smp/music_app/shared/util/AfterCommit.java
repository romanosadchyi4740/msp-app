package com.romanosadchyi.smp.music_app.shared.util;

import lombok.experimental.UtilityClass;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs work, such as sending a STOMP message, only once the current transaction has committed, so nobody hears of a
 * change that was rolled back. Outside a transaction it runs at once.
 */
@UtilityClass
public class AfterCommit {

    public void run(Runnable work) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            work.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                work.run();
            }
        });
    }
}
