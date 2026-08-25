package dev.micx.micxfabric;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/** Cross-restart memory for which panel channel was last selected. */
public final class PanelState {
    private static final String FILE = "micx-panel.properties";
    private static final String KEY = "lastModule";
    private static String cached = null;

    private PanelState() {}

    private static Path file() {
        return FabricRuntime.configPath().resolve(FILE);
    }

    public static String lastModule() {
        if (cached != null) return cached.isEmpty() ? null : cached;
        Properties p = ConfigProperties.load(file(), null);
        cached = p.getProperty(KEY, "");
        return cached.isEmpty() ? null : cached;
    }

    public static void setLastModule(String fullKey) {
        cached = fullKey == null ? "" : fullKey;
        Properties p = new Properties();
        p.setProperty(KEY, cached);
        try {
            AtomicProperties.store(file(), p, "MICx panel state");
        } catch (IOException ignored) {}
    }
}
