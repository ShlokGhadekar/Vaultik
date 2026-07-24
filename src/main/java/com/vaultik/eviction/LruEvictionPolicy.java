package com.vaultik.eviction;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Tracks keys by recency. The store invokes this policy while holding its write lock.
 */
public class LruEvictionPolicy implements EvictionPolicy {
    private final LinkedHashSet<String> order = new LinkedHashSet<>();

    @Override
    public void onGet(String key) {
        touch(key);
    }

    @Override
    public void onPut(String key) {
        touch(key);
    }

    @Override
    public void onRemove(String key) {
        order.remove(key);
    }

    @Override
    public Optional<String> evictCandidate(Set<String> keys) {
        return order.stream().filter(keys::contains).findFirst();
    }

    @Override
    public String name() {
        return "LRU";
    }

    private void touch(String key) {
        order.remove(key);
        order.add(key);
    }
}
