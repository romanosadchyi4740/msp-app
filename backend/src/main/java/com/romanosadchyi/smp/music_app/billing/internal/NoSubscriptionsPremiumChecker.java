package com.romanosadchyi.smp.music_app.billing.internal;

import com.romanosadchyi.smp.music_app.billing.external.PremiumChecker;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Nobody has Premium until subscriptions exist; KAN-54 replaces this with a check of the user's active subscription.
 */
@Component
class NoSubscriptionsPremiumChecker implements PremiumChecker {

    @Override
    public boolean hasPremium(UUID userId) {
        return false;
    }
}
