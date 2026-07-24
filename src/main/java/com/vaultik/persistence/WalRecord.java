package com.vaultik.persistence;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

public record WalRecord(WalOperation operation, String key, Serializable value, Instant expiresAt) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static WalRecord set(String key, Serializable value, Instant expiresAt) {
        return new WalRecord(WalOperation.SET, key, value, expiresAt);
    }

    public static WalRecord delete(String key) {
        return new WalRecord(WalOperation.DELETE, key, null, null);
    }
}
