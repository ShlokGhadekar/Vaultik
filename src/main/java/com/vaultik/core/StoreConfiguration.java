package com.vaultik.core;

import java.nio.file.Path;

public record StoreConfiguration(
        int capacity,
        Path walPath,
        Path snapshotPath,
        long snapshotInterval
) {
    public StoreConfiguration {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (snapshotInterval <= 0) {
            throw new IllegalArgumentException("snapshotInterval must be positive");
        }
    }
}
