package com.vaultik.benchmark;

import com.vaultik.core.KeyValueStore;
import com.vaultik.core.StoreConfiguration;
import com.vaultik.eviction.EvictionPolicyFactory;
import com.vaultik.eviction.EvictionPolicyType;
import com.vaultik.persistence.FileSnapshotStore;
import com.vaultik.persistence.FileWriteAheadLog;
import com.vaultik.storage.PersistentKeyValueStore;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BenchmarkRunner {
    public BenchmarkReport run(EvictionPolicyType policy, AccessPattern pattern, int threads, int operations)
            throws IOException, InterruptedException {
        var directory = Files.createTempDirectory("vaultik-bench");
        StoreConfiguration configuration = new StoreConfiguration(
                20_000,
                directory.resolve("bench.wal"),
                directory.resolve("bench.snapshot"),
                Long.MAX_VALUE
        );
        try (KeyValueStore store = new PersistentKeyValueStore(
                configuration,
                EvictionPolicyFactory.create(policy),
                new FileWriteAheadLog(configuration.walPath()),
                new FileSnapshotStore(configuration.snapshotPath()))) {
            for (int i = 0; i < 10_000; i++) {
                store.set("key-" + i, "value-" + i, Duration.ofMinutes(5));
            }

            long[] latencies = new long[operations];
            AtomicInteger cursor = new AtomicInteger();
            long start = System.nanoTime();
            var executor = Executors.newFixedThreadPool(threads);
            for (int i = 0; i < threads; i++) {
                executor.submit(() -> executeWorkload(store, pattern, operations, cursor, latencies));
            }
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.MINUTES);
            long elapsedNanos = System.nanoTime() - start;

            Arrays.sort(latencies);
            return new BenchmarkReport(
                    policy.name(),
                    pattern,
                    threads,
                    operations,
                    operations / (elapsedNanos / 1_000_000_000.0),
                    averageMicros(latencies),
                    percentileMicros(latencies, 0.95),
                    percentileMicros(latencies, 0.99)
            );
        }
    }

    private void executeWorkload(
            KeyValueStore store,
            AccessPattern pattern,
            int operations,
            AtomicInteger cursor,
            long[] latencies
    ) {
        Random random = new Random();
        while (true) {
            int index = cursor.getAndIncrement();
            if (index >= operations) {
                return;
            }
            String key = pattern == AccessPattern.HOT_KEY
                    ? "key-" + random.nextInt(100)
                    : "key-" + random.nextInt(10_000);
            long start = System.nanoTime();
            if (index % 10 == 0) {
                store.set(key, "updated-" + index);
            } else {
                store.get(key);
            }
            latencies[index] = System.nanoTime() - start;
        }
    }

    private double averageMicros(long[] latencies) {
        return Arrays.stream(latencies).average().orElse(0) / 1_000.0;
    }

    private double percentileMicros(long[] latencies, double percentile) {
        int index = Math.min(latencies.length - 1, (int) Math.ceil(percentile * latencies.length) - 1);
        return latencies[index] / 1_000.0;
    }
}
