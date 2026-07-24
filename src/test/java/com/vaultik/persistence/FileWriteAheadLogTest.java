package com.vaultik.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileWriteAheadLogTest {
    @TempDir
    Path directory;

    @Test
    void replaysAppendedRecordsInOrder() throws Exception {
        FileWriteAheadLog wal = new FileWriteAheadLog(directory.resolve("vaultik.wal"));

        wal.append(WalRecord.set("a", "1", null));
        wal.append(WalRecord.delete("a"));

        assertThat(wal.readAll())
                .extracting(WalRecord::operation)
                .containsExactly(WalOperation.SET, WalOperation.DELETE);
    }
}
