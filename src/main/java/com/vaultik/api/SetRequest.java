package com.vaultik.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.io.Serializable;

public record SetRequest(
        @NotBlank String key,
        @NotNull Serializable value,
        @Positive Long ttlSeconds
) {
}
