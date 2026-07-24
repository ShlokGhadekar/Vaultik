package com.vaultik.eviction;

public final class EvictionPolicyFactory {
    private EvictionPolicyFactory() {
    }

    public static EvictionPolicy create(EvictionPolicyType type) {
        return switch (type) {
            case LRU -> new LruEvictionPolicy();
            case LFU -> new LfuEvictionPolicy();
        };
    }
}
