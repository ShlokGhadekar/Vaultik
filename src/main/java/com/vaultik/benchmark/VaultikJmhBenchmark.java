package com.vaultik.benchmark;

import com.vaultik.core.KeyValueStore;
import com.vaultik.core.StoreConfiguration;
import com.vaultik.eviction.EvictionPolicyFactory;
import com.vaultik.eviction.EvictionPolicyType;
import com.vaultik.persistence.FileSnapshotStore;
import com.vaultik.persistence.FileWriteAheadLog;
import com.vaultik.storage.PersistentKeyValueStore;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class VaultikJmhBenchmark {
    @Benchmark
    public void mixedReadWrite(EngineState state) {
        int index = state.random.nextInt(10_000);
        String key = state.hotKeys ? "key-" + (index % 100) : "key-" + index;
        if (index % 10 == 0) {
            state.store.set(key, "updated-" + index);
        } else {
            state.store.get(key);
        }
    }

    @State(Scope.Benchmark)
    public static class EngineState {
        @Param({"LRU", "LFU"})
        public EvictionPolicyType policy;

        @Param({"false", "true"})
        public boolean hotKeys;

        KeyValueStore store;
        Random random;
        Path directory;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            random = new Random(42);
            directory = Files.createTempDirectory("vaultik-jmh");
            StoreConfiguration configuration = new StoreConfiguration(
                    20_000,
                    directory.resolve("jmh.wal"),
                    directory.resolve("jmh.snapshot"),
                    Long.MAX_VALUE
            );
            store = new PersistentKeyValueStore(
                    configuration,
                    EvictionPolicyFactory.create(policy),
                    new FileWriteAheadLog(configuration.walPath()),
                    new FileSnapshotStore(configuration.snapshotPath())
            );
            for (int i = 0; i < 10_000; i++) {
                store.set("key-" + i, "value-" + i);
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() throws Exception {
            store.close();
        }
    }
}
