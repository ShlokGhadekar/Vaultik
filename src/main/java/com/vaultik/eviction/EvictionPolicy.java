package com.vaultik.eviction;

import java.util.Optional;
import java.util.Set;

public interface EvictionPolicy {
    void onGet(String key);

    void onPut(String key);

    void onRemove(String key);

    Optional<String> evictCandidate(Set<String> keys);

    String name();
}
