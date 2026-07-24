package com.vaultik.api;

import java.io.Serializable;

public record ValueResponse(String key, Serializable value) {
}
