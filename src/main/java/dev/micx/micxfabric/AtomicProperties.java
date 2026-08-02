package dev.micx.micxfabric;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Small properties helper that keeps the previous file if replacement fails. */
public final class AtomicProperties {
    private AtomicProperties() {
    }

    public static Properties load(Path file) throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
            }
        }
        return properties;
    }

    public static void store(Path file, Properties properties, String comment) throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, comment);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }
}
