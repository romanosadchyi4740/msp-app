package com.romanosadchyi.smp.music_app.billing.internal;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NoSubscriptionsPremiumCheckerTest {

    @Test
    void hasPremium_NobodyBeforeSubscriptionsExist() {
        assertThat(new NoSubscriptionsPremiumChecker().hasPremium(UUID.randomUUID())).isFalse();
    }
}
