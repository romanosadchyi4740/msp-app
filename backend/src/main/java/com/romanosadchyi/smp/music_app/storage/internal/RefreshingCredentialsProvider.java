package com.romanosadchyi.smp.music_app.storage.internal;

import io.minio.credentials.Credentials;
import io.minio.credentials.Provider;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Keeps temporary credentials (the EC2 instance role) fresh enough for presigned URLs. A presigned URL stops working
 * when the session token it was signed with expires, and {@code IamAwsProvider} keeps its credentials until 10 s
 * before that, so a URL promised for an hour could die after minutes. This provider fetches new credentials once the
 * cached ones have less than {@code minRemaining} left, at most once per {@link #RETRY_INTERVAL} while the source
 * still returns the old ones (the instance metadata rotates them some time before they expire).
 */
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class RefreshingCredentialsProvider implements Provider {

    static final Duration RETRY_INTERVAL = Duration.ofMinutes(1);

    /**
     * Makes a new source for every refresh, because sources such as {@code IamAwsProvider} cache on their own.
     */
    Supplier<Provider> sourceFactory;
    @Getter(AccessLevel.PACKAGE)
    Duration minRemaining;
    Clock clock;

    @NonFinal
    Credentials credentials;
    @NonFinal
    Instant lastFetch;

    @Override
    public synchronized Credentials fetch() {
        Instant now = clock.instant();
        if (credentials == null || needsRefresh(now)) {
            credentials = sourceFactory.get().fetch();
            lastFetch = now;
        }
        return credentials;
    }

    private boolean needsRefresh(Instant now) {
        if (credentials.expiration() == null) {
            return false;
        }
        Instant expiration = credentials.expiration().toInstant();
        boolean expired = !expiration.isAfter(now);
        boolean runningOut = expiration.isBefore(now.plus(minRemaining));
        boolean retryDue = !lastFetch.plus(RETRY_INTERVAL).isAfter(now);
        return expired || runningOut && retryDue;
    }
}
