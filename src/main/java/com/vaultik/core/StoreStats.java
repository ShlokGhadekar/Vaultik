package com.vaultik.core;

public record StoreStats(long size, long capacity, long hits, long misses, long evictions, long writes, long deletes) {
}
