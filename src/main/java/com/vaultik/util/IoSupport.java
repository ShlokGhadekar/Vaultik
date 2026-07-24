package com.vaultik.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;

public final class IoSupport {
    private IoSupport() {
    }

    public static void createParentDirectories(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    public static byte[] serialize(Serializable value) throws IOException {
        try (var buffer = new ByteArrayOutputStream();
             var output = new ObjectOutputStream(buffer)) {
            output.writeObject(value);
            output.flush();
            return buffer.toByteArray();
        }
    }

    public static Object deserialize(byte[] payload) throws IOException, ClassNotFoundException {
        try (var input = new ObjectInputStream(new ByteArrayInputStream(payload))) {
            return input.readObject();
        }
    }

    public static void writeLengthPrefixed(OutputStream output, byte[] payload) throws IOException {
        var data = new DataOutputStream(output);
        data.writeInt(payload.length);
        data.write(payload);
    }
}
