package dev.micx.micxfabric;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ModuleStateStore {
    private static final Properties STATES = new Properties();
    private static Path file;

    private ModuleStateStore() {
    }

    public static synchronized void initialize(Path directory) {
        file = directory.resolve("module-states.properties");
        try {
            Files.createDirectories(directory);
            Path legacy = directory.resolve("module-state.properties");
            Path source = Files.isRegularFile(file) ? file : legacy;
            if (Files.isRegularFile(source)) {
                try (InputStream input = Files.newInputStream(source)) {
                    STATES.clear();
                    STATES.load(input);
                }
            }
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to load MICx module states", exception);
        }
    }

    public static synchronized boolean get(String id, boolean fallback) {
        String value = STATES.getProperty(id);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    public static synchronized void put(String id, boolean enabled) {
        STATES.setProperty(id, Boolean.toString(enabled));
        if (file == null) return;
        try {
            Properties snapshot = new Properties();
            snapshot.putAll(STATES);
            AtomicProperties.store(file, snapshot, "MICx Toolkit module states");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save MICx module states", exception);
        }
    }
}
