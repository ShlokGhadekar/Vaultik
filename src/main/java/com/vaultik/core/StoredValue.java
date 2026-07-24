package com.vaultik.core;

import java.io.Serial;
import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Immutable value wrapper stored by the engine.
 */
public record StoredValue(Serializable value, Instant expiresAt) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static StoredValue neverExpiring(Serializable value) {
        return new StoredValue(value, null);
    }

    public static StoredValue expiring(Serializable value, Instant expiresAt) {
        return new StoredValue(value, expiresAt);
    }

    public Optional<Instant> expiration() {
        return Optional.ofNullable(expiresAt);
    }

    public boolean isExpired(Clock clock) {
        return expiresAt != null && !expiresAt.isAfter(clock.instant());
    }
}
