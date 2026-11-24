package com.romanosadchyi.smp.music_app.storage.internal;

import io.minio.Time;
import io.minio.credentials.Credentials;
import io.minio.credentials.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshingCredentialsProviderTest {

    static final Instant START = Instant.parse("2026-11-22T12:00:00Z");
    static final Duration MIN_REMAINING = Duration.ofMinutes(65);

    MutableClock clock;
    Deque<Credentials> issued;
    int sourcesMade;
    RefreshingCredentialsProvider provider;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        issued = new ArrayDeque<>();
        provider = new RefreshingCredentialsProvider(() -> {
            sourcesMade++;
            Credentials next = issued.size() > 1 ? issued.poll() : issued.peek();
            return (Provider) () -> next;
        }, MIN_REMAINING, clock);
    }

    @Test
    void fetch_FreshCredentials_AreCachedWithoutAskingTheSourceAgain() {
        issued.add(credentials("first", START.plus(Duration.ofHours(6))));

        provider.fetch();
        clock.advance(Duration.ofHours(4));

        assertThat(provider.fetch().accessKey()).isEqualTo("first");
        assertThat(sourcesMade).isEqualTo(1);
    }

    @Test
    void fetch_LessLeftThanAPresignedUrlNeeds_FetchesNewOnes() {
        issued.add(credentials("first", START.plus(Duration.ofHours(1))));
        issued.add(credentials("second", START.plus(Duration.ofHours(7))));

        assertThat(provider.fetch().accessKey()).isEqualTo("first");
        clock.advance(Duration.ofMinutes(1));

        assertThat(provider.fetch().accessKey()).isEqualTo("second");
        assertThat(sourcesMade).isEqualTo(2);
    }

    @Test
    void fetch_SourceStillReturnsTheOldOnes_AsksAtMostOncePerRetryInterval() {
        issued.add(credentials("old", START.plus(Duration.ofMinutes(30))));

        provider.fetch();
        clock.advance(Duration.ofSeconds(30));
        provider.fetch();
        provider.fetch();

        assertThat(sourcesMade).isEqualTo(1);
        clock.advance(RefreshingCredentialsProvider.RETRY_INTERVAL);
        provider.fetch();
        assertThat(sourcesMade).isEqualTo(2);
    }

    @Test
    void fetch_Expired_FetchesAtOnceEvenWithinTheRetryInterval() {
        issued.add(credentials("first", START.plus(Duration.ofSeconds(20))));
        issued.add(credentials("second", START.plus(Duration.ofHours(6))));

        provider.fetch();
        clock.advance(Duration.ofSeconds(30));

        assertThat(provider.fetch().accessKey()).isEqualTo("second");
    }

    @Test
    void fetch_CredentialsWithoutExpiration_AreKept() {
        issued.add(new Credentials("static", "secret", null, null));

        provider.fetch();
        clock.advance(Duration.ofDays(30));
        provider.fetch();

        assertThat(sourcesMade).isEqualTo(1);
    }

    private static Credentials credentials(String accessKey, Instant expiration) {
        return new Credentials(accessKey, "secret", "session-token",
                new Time.S3Time(expiration.atZone(ZoneOffset.UTC)));
    }

    static class MutableClock extends Clock {

        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
