package com.vaultik.persistence;

import com.vaultik.core.StoredValue;

import java.io.IOException;
import java.util.Map;

public interface SnapshotStore {
    void save(Map<String, StoredValue> entries) throws IOException;

    Map<String, StoredValue> load() throws IOException;
}
