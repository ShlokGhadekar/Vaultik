package com.vaultik.persistence;

import com.vaultik.core.StoredValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FileSnapshotStoreTest {
    @TempDir
    Path directory;

    @Test
    void savesAndLoadsEntries() throws Exception {
        FileSnapshotStore snapshots = new FileSnapshotStore(directory.resolve("vaultik.snapshot"));

        snapshots.save(Map.of("a", StoredValue.neverExpiring("1")));

        assertThat(snapshots.load()).containsKey("a");
        assertThat(snapshots.load().get("a").value()).isEqualTo("1");
    }
}
