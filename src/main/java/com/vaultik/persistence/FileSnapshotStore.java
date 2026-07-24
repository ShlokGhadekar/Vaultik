package com.vaultik.persistence;

import com.vaultik.core.StoredValue;
import com.vaultik.util.IoSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

public class FileSnapshotStore implements SnapshotStore {
    private final Path path;

    public FileSnapshotStore(Path path) {
        this.path = path;
    }

    @Override
    public synchronized void save(Map<String, StoredValue> entries) throws IOException {
        IoSupport.createParentDirectories(path);
        byte[] bytes = IoSupport.serialize(new HashMap<>(entries));
        Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized Map<String, StoredValue> load() throws IOException {
        if (!Files.exists(path) || Files.size(path) == 0) {
            return new HashMap<>();
        }
        try {
            return (Map<String, StoredValue>) IoSupport.deserialize(Files.readAllBytes(path));
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to deserialize snapshot", e);
        }
    }
}
