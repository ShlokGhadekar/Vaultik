package com.vaultik.eviction;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Tracks approximate frequency with insertion sequence as a deterministic tie-breaker.
 */
public class LfuEvictionPolicy implements EvictionPolicy {
    private final Map<String, Long> frequencies = new HashMap<>();
    private final Map<String, Long> sequence = new HashMap<>();
    private long nextSequence;

    @Override
    public void onGet(String key) {
        frequencies.computeIfPresent(key, (ignored, count) -> count + 1);
    }

    @Override
    public void onPut(String key) {
        frequencies.merge(key, 1L, Long::sum);
        sequence.putIfAbsent(key, nextSequence++);
    }

    @Override
    public void onRemove(String key) {
        frequencies.remove(key);
        sequence.remove(key);
    }

    @Override
    public Optional<String> evictCandidate(Set<String> keys) {
        return keys.stream()
                .filter(frequencies::containsKey)
                .min((left, right) -> {
                    int frequency = Long.compare(frequencies.get(left), frequencies.get(right));
                    return frequency != 0 ? frequency : Long.compare(sequence.get(left), sequence.get(right));
                });
    }

    @Override
    public String name() {
        return "LFU";
    }
}
