package dev.micx.micxfabric;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Persisted independent width/height multipliers for screen HUD elements. */
public final class HudLayoutConfig {
    private static final float DEFAULT = 1.0f;
    private final Map<String, Scale> scales = new LinkedHashMap<>();
    private final Path currentFile;
    private final Path legacyFile;
    private boolean loaded;

    private HudLayoutConfig() {
        this(FabricRuntime.configPath().resolve("hud-layout.properties"),
                FabricRuntime.configPath().getParent().resolve("MICxToolkit_HudLayout.cfg"));
    }

    HudLayoutConfig(Path currentFile, Path legacyFile) {
        this.currentFile = currentFile;
        this.legacyFile = legacyFile;
    }

    public static HudLayoutConfig instance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final HudLayoutConfig INSTANCE = new HudLayoutConfig();
    }

    public synchronized float scaleX(String id) {
        load();
        return scale(id).x;
    }

    public synchronized float scaleY(String id) {
        load();
        return scale(id).y;
    }

    public synchronized void setScale(String id, float x, float y) {
        if (id == null || id.isBlank()) return;
        load();
        scales.put(id, new Scale(HudLayoutMath.clampScale(x), HudLayoutMath.clampScale(y)));
    }

    public synchronized void reset(String id) {
        if (id == null || id.isBlank()) return;
        load();
        scales.remove(id);
    }

    public synchronized void resetAll() {
        load();
        scales.clear();
    }

    public synchronized void save() {
        load();
        Properties properties = new Properties();
        for (Map.Entry<String, Scale> entry : scales.entrySet()) {
            properties.setProperty(entry.getKey() + ".scaleX", Float.toString(entry.getValue().x));
            properties.setProperty(entry.getKey() + ".scaleY", Float.toString(entry.getValue().y));
        }
        try {
            AtomicProperties.store(currentFile, properties, "MICx HUD layout configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save HUD layout configuration", exception);
        }
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        Properties properties = ConfigProperties.load(currentFile, legacyFile);
        for (String key : properties.stringPropertyNames()) {
            if (!key.endsWith(".scaleX") && !key.endsWith(".scaleY")) continue;
            String id = key.substring(0, key.lastIndexOf('.'));
            Scale prior = scales.getOrDefault(id, new Scale(DEFAULT, DEFAULT));
            float value = parse(properties.getProperty(key), DEFAULT);
            scales.put(id, key.endsWith(".scaleX") ? new Scale(value, prior.y) : new Scale(prior.x, value));
        }
    }

    private Scale scale(String id) {
        if (id == null || id.isBlank()) return new Scale(DEFAULT, DEFAULT);
        return scales.getOrDefault(id, new Scale(DEFAULT, DEFAULT));
    }

    private static float parse(String value, float fallback) {
        try {
            return HudLayoutMath.clampScale(Float.parseFloat(value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private record Scale(float x, float y) {
    }
}
