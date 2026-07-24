package com.vaultik.storage;

import com.vaultik.core.KeyValueStore;
import com.vaultik.core.StoreConfiguration;
import com.vaultik.core.StoreStats;
import com.vaultik.core.StoredValue;
import com.vaultik.eviction.EvictionPolicy;
import com.vaultik.persistence.SnapshotStore;
import com.vaultik.persistence.WalOperation;
import com.vaultik.persistence.WalRecord;
import com.vaultik.persistence.WriteAheadLog;

import java.io.IOException;
import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe storage engine independent of Spring.
 *
 * <p>A ReadWriteLock is used because GET is expected to dominate many KV workloads and
 * can safely run concurrently. A synchronized method or a single global mutex would
 * serialize readers unnecessarily. Per-key locking can improve write parallelism, but
 * it complicates eviction, snapshotting, and multi-step recovery invariants; for an
 * interview-friendly single-node engine this lock keeps correctness easy to reason about.</p>
 */
public class PersistentKeyValueStore implements KeyValueStore {
    private final Map<String, StoredValue> entries = new HashMap<>();
    private final StoreConfiguration configuration;
    private final EvictionPolicy evictionPolicy;
    private final WriteAheadLog wal;
    private final SnapshotStore snapshotStore;
    private final Clock clock;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder evictions = new LongAdder();
    private final LongAdder writes = new LongAdder();
    private final LongAdder deletes = new LongAdder();
    private long writesSinceSnapshot;

    public PersistentKeyValueStore(
            StoreConfiguration configuration,
            EvictionPolicy evictionPolicy,
            WriteAheadLog wal,
            SnapshotStore snapshotStore
    ) throws IOException {
        this(configuration, evictionPolicy, wal, snapshotStore, Clock.systemUTC());
    }

    public PersistentKeyValueStore(
            StoreConfiguration configuration,
            EvictionPolicy evictionPolicy,
            WriteAheadLog wal,
            SnapshotStore snapshotStore,
            Clock clock
    ) throws IOException {
        this.configuration = configuration;
        this.evictionPolicy = evictionPolicy;
        this.wal = wal;
        this.snapshotStore = snapshotStore;
        this.clock = clock;
        recover();
    }

    @Override
    public void set(String key, Serializable value) {
        set(key, value, null);
    }

    @Override
    public void set(String key, Serializable value, Duration ttl) {
        validateKey(key);
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        if (ttl != null && (ttl.isZero() || ttl.isNegative())) {
            throw new IllegalArgumentException("ttl must be positive");
        }

        Instant expiresAt = ttl == null ? null : clock.instant().plus(ttl);
        lock.writeLock().lock();
        try {
            append(WalRecord.set(key, value, expiresAt));
            entries.put(key, new StoredValue(value, expiresAt));
            evictionPolicy.onPut(key);
            evictIfNeeded();
            writes.increment();
            checkpointIfNeeded();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<Serializable> get(String key) {
        validateKey(key);
        lock.writeLock().lock();
        try {
            StoredValue storedValue = entries.get(key);
            if (storedValue == null) {
                misses.increment();
                return Optional.empty();
            }
            if (storedValue.isExpired(clock)) {
                removeInMemory(key);
                append(WalRecord.delete(key));
                misses.increment();
                deletes.increment();
                checkpointIfNeeded();
                return Optional.empty();
            }
            evictionPolicy.onGet(key);
            hits.increment();
            return Optional.of(storedValue.value());
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean delete(String key) {
        validateKey(key);
        lock.writeLock().lock();
        try {
            append(WalRecord.delete(key));
            StoredValue removed = removeInMemory(key);
            deletes.increment();
            checkpointIfNeeded();
            return removed != null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public StoreStats stats() {
        lock.readLock().lock();
        try {
            return new StoreStats(
                    entries.size(),
                    configuration.capacity(),
                    hits.sum(),
                    misses.sum(),
                    evictions.sum(),
                    writes.sum(),
                    deletes.sum()
            );
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void close() throws IOException {
        lock.writeLock().lock();
        try {
            snapshotStore.save(entries);
            wal.truncate();
            wal.close();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void recover() throws IOException {
        lock.writeLock().lock();
        try {
            entries.clear();
            entries.putAll(snapshotStore.load());
            for (String key : entries.keySet()) {
                evictionPolicy.onPut(key);
            }
            for (WalRecord record : wal.readAll()) {
                applyRecovered(record);
            }
            purgeExpired();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void applyRecovered(WalRecord record) {
        if (record.operation() == WalOperation.SET) {
            entries.put(record.key(), new StoredValue(record.value(), record.expiresAt()));
            evictionPolicy.onPut(record.key());
            evictIfNeeded();
        } else {
            removeInMemory(record.key());
        }
    }

    private void evictIfNeeded() {
        while (entries.size() > configuration.capacity()) {
            String key = evictionPolicy.evictCandidate(entries.keySet())
                    .orElseThrow(() -> new IllegalStateException("eviction policy produced no candidate"));
            removeInMemory(key);
            evictions.increment();
        }
    }

    private void checkpointIfNeeded() {
        writesSinceSnapshot++;
        if (writesSinceSnapshot >= configuration.snapshotInterval()) {
            try {
                snapshotStore.save(entries);
                wal.truncate();
                writesSinceSnapshot = 0;
            } catch (IOException e) {
                throw new StorageException("Unable to checkpoint snapshot", e);
            }
        }
    }

    private void append(WalRecord record) {
        try {
            wal.append(record);
        } catch (IOException e) {
            throw new StorageException("Unable to append WAL record", e);
        }
    }

    private StoredValue removeInMemory(String key) {
        evictionPolicy.onRemove(key);
        return entries.remove(key);
    }

    private void purgeExpired() {
        entries.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired(clock);
            if (expired) {
                evictionPolicy.onRemove(entry.getKey());
            }
            return expired;
        });
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
    }
}
