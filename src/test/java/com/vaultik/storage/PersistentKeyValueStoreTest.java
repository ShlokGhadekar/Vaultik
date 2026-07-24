package com.vaultik.storage;

import com.vaultik.core.KeyValueStore;
import com.vaultik.core.StoreConfiguration;
import com.vaultik.eviction.EvictionPolicyFactory;
import com.vaultik.eviction.EvictionPolicyType;
import com.vaultik.persistence.FileSnapshotStore;
import com.vaultik.persistence.FileWriteAheadLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class PersistentKeyValueStoreTest {
    @TempDir
    Path directory;

    @Test
    void setAndGetReturnsStoredValue() throws Exception {
        try (KeyValueStore store = newStore(EvictionPolicyType.LRU, 10, 100)) {
            store.set("language", "java");

            assertThat(store.get("language")).contains("java");
            assertThat(store.stats().hits()).isEqualTo(1);
        }
    }

    @Test
    void deleteRemovesValue() throws Exception {
        try (KeyValueStore store = newStore(EvictionPolicyType.LRU, 10, 100)) {
            store.set("session", "abc");

            assertThat(store.delete("session")).isTrue();
            assertThat(store.get("session")).isEmpty();
        }
    }

    @Test
    void ttlExpiresOnAccess() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        try (KeyValueStore store = newStore(EvictionPolicyType.LRU, 10, 100, clock)) {
            store.set("token", "secret", Duration.ofSeconds(5));
            clock.advance(Duration.ofSeconds(6));

            assertThat(store.get("token")).isEmpty();
            assertThat(store.stats().deletes()).isEqualTo(1);
        }
    }

    @Test
    void lruEvictsLeastRecentlyUsedEntry() throws Exception {
        try (KeyValueStore store = newStore(EvictionPolicyType.LRU, 2, 100)) {
            store.set("a", "1");
            store.set("b", "2");
            store.get("a");
            store.set("c", "3");

            assertThat(store.get("a")).contains("1");
            assertThat(store.get("b")).isEmpty();
            assertThat(store.get("c")).contains("3");
        }
    }

    @Test
    void lfuEvictsLeastFrequentlyUsedEntry() throws Exception {
        try (KeyValueStore store = newStore(EvictionPolicyType.LFU, 2, 100)) {
            store.set("a", "1");
            store.set("b", "2");
            store.get("a");
            store.get("a");
            store.set("c", "3");

            assertThat(store.get("a")).contains("1");
            assertThat(store.get("b")).isEmpty();
            assertThat(store.get("c")).contains("3");
        }
    }

    @Test
    void walReplayRestoresWritesAfterRestart() throws Exception {
        StoreConfiguration configuration = configuration(10, 100);
        KeyValueStore first = createStore(configuration, EvictionPolicyType.LRU, Clock.systemUTC());
        first.set("name", "vaultik");
        first.set("version", "0.1.0");
        first.delete("version");

        try (KeyValueStore recovered = createStore(configuration, EvictionPolicyType.LRU, Clock.systemUTC())) {
            assertThat(recovered.get("name")).contains("vaultik");
            assertThat(recovered.get("version")).isEmpty();
        }
        first.close();
    }

    @Test
    void snapshotLoadingRestoresCheckpointedEntries() throws Exception {
        StoreConfiguration configuration = configuration(10, 1);
        try (KeyValueStore first = createStore(configuration, EvictionPolicyType.LRU, Clock.systemUTC())) {
            first.set("snapshot-key", "snapshot-value");
        }

        try (KeyValueStore recovered = createStore(configuration, EvictionPolicyType.LRU, Clock.systemUTC())) {
            assertThat(recovered.get("snapshot-key")).contains("snapshot-value");
        }
    }

    @Test
    void concurrentAccessRemainsThreadSafe() throws Exception {
        try (KeyValueStore store = newStore(EvictionPolicyType.LRU, 2_000, 10_000)) {
            var executor = Executors.newFixedThreadPool(8);
            var tasks = new ArrayList<Callable<Void>>();
            for (int thread = 0; thread < 8; thread++) {
                int threadId = thread;
                tasks.add(() -> {
                    for (int i = 0; i < 250; i++) {
                        String key = "key-" + threadId + "-" + i;
                        store.set(key, i);
                        assertThat(store.get(key)).contains(i);
                    }
                    return null;
                });
            }

            for (var result : executor.invokeAll(tasks)) {
                result.get();
            }
            executor.shutdown();

            assertThat(store.stats().size()).isEqualTo(2_000);
        }
    }

    private KeyValueStore newStore(EvictionPolicyType policy, int capacity, long snapshotInterval) throws IOException {
        return newStore(policy, capacity, snapshotInterval, Clock.systemUTC());
    }

    private KeyValueStore newStore(
            EvictionPolicyType policy,
            int capacity,
            long snapshotInterval,
            Clock clock
    ) throws IOException {
        return createStore(configuration(capacity, snapshotInterval), policy, clock);
    }

    private StoreConfiguration configuration(int capacity, long snapshotInterval) {
        return new StoreConfiguration(
                capacity,
                directory.resolve("vaultik.wal"),
                directory.resolve("vaultik.snapshot"),
                snapshotInterval
        );
    }

    private KeyValueStore createStore(StoreConfiguration configuration, EvictionPolicyType policy, Clock clock)
            throws IOException {
        return new PersistentKeyValueStore(
                configuration,
                EvictionPolicyFactory.create(policy),
                new FileWriteAheadLog(configuration.walPath()),
                new FileSnapshotStore(configuration.snapshotPath()),
                clock
        );
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
