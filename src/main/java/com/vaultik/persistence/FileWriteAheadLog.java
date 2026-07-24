package com.vaultik.persistence;

import com.vaultik.util.IoSupport;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Append-only WAL. Each record is length-prefixed so replay can stop cleanly after a partial tail write.
 */
public class FileWriteAheadLog implements WriteAheadLog {
    private final Path path;

    public FileWriteAheadLog(Path path) {
        this.path = path;
    }

    @Override
    public synchronized void append(WalRecord record) throws IOException {
        IoSupport.createParentDirectories(path);
        try (var output = new BufferedOutputStream(Files.newOutputStream(
                path, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            byte[] payload = IoSupport.serialize(record);
            IoSupport.writeLengthPrefixed(output, payload);
            output.flush();
        }
    }

    @Override
    public synchronized List<WalRecord> readAll() throws IOException {
        if (!Files.exists(path)) {
            return List.of();
        }
        List<WalRecord> records = new ArrayList<>();
        try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            while (true) {
                try {
                    int length = input.readInt();
                    if (length <= 0) {
                        break;
                    }
                    byte[] payload = input.readNBytes(length);
                    if (payload.length != length) {
                        break;
                    }
                    records.add((WalRecord) IoSupport.deserialize(payload));
                } catch (EOFException eof) {
                    break;
                } catch (ClassNotFoundException e) {
                    throw new IOException("Unable to deserialize WAL record", e);
                }
            }
        }
        return records;
    }

    @Override
    public synchronized void truncate() throws IOException {
        IoSupport.createParentDirectories(path);
        Files.write(path, new byte[0], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Override
    public void close() {
        // File handles are opened per operation to keep the implementation simple and crash-friendly.
    }
}
