package com.vaultik.core;

import java.io.Closeable;
import java.io.Serializable;
import java.time.Duration;
import java.util.Optional;

public interface KeyValueStore extends Closeable {
    void set(String key, Serializable value);

    void set(String key, Serializable value, Duration ttl);

    Optional<Serializable> get(String key);

    boolean delete(String key);

    StoreStats stats();
}
