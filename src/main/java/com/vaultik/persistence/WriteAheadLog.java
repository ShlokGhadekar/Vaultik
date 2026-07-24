package com.vaultik.persistence;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

public interface WriteAheadLog extends Closeable {
    void append(WalRecord record) throws IOException;

    List<WalRecord> readAll() throws IOException;

    void truncate() throws IOException;
}
