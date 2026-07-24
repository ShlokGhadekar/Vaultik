package com.vaultik.api;

import com.vaultik.core.StoreStats;

public record StatsResponse(long size, long capacity, long hits, long misses, long evictions, long writes, long deletes) {
    public static StatsResponse from(StoreStats stats) {
        return new StatsResponse(
                stats.size(),
                stats.capacity(),
                stats.hits(),
                stats.misses(),
                stats.evictions(),
                stats.writes(),
                stats.deletes()
        );
    }
}
