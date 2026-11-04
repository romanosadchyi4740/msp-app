package com.romanosadchyi.smp.music_app.billing.external;

import java.util.UUID;

/**
 * Whether a user's plan lifts the free plan's limits, such as the 40-minute lesson.
 */
public interface PremiumChecker {

    boolean hasPremium(UUID userId);
}
