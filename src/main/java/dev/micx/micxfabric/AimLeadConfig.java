package dev.micx.micxfabric;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/** Forge-compatible AimLead settings stored in the Fabric config directory. */
public final class AimLeadConfig {
    public boolean autoPing = true;
    public boolean gameRtt = true;
    public boolean fireDot = true;
    public boolean drawLink = true;
    public boolean serverShadow = false;
    public boolean zombiesOnly = true;
    public int minDist = 12;
    public int maxGhosts = 4;
    public int extraMs = 100;
    public int manualPing = 250;
    public int markerOffsetX;
    public int markerOffsetY = 6;
    public float markerScaleX = 1.0f;
    public float markerScaleY = 1.0f;

    private boolean loaded;

    public void load() {
        if (loaded) return;
        loaded = true;
        Path current = FabricRuntime.configPath().resolve("aim-lead.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_AimLead.cfg");
        Properties properties = ConfigProperties.load(current, legacy);
        autoPing = ConfigProperties.bool(properties, "autoPing", true);
        gameRtt = ConfigProperties.bool(properties, "gameRtt", true);
        fireDot = ConfigProperties.bool(properties, "fireDot", true);
        drawLink = ConfigProperties.bool(properties, "drawLink", true);
        serverShadow = ConfigProperties.bool(properties, "serverShadow", false);
        zombiesOnly = ConfigProperties.bool(properties, "zombiesOnly", true);
        minDist = ConfigProperties.integer(properties, "minDist", 12, 5, 30);
        maxGhosts = ConfigProperties.integer(properties, "maxGhosts", 4, 1, 8);
        extraMs = ConfigProperties.integer(properties, "extraMs", 100, -100, 400);
        manualPing = ConfigProperties.integer(properties, "manualPing", 250, 0, 600);
        markerOffsetX = ConfigProperties.integer(properties, "markerOffsetX", 0, -2_000, 2_000);
        markerOffsetY = ConfigProperties.integer(properties, "markerOffsetY", 6, -2_000, 2_000);
        markerScaleX = parseScale(ConfigProperties.string(properties, "markerScaleX", "1.0"));
        markerScaleY = parseScale(ConfigProperties.string(properties, "markerScaleY", "1.0"));
    }

    public void save() {
        Properties properties = new Properties();
        properties.setProperty("autoPing", Boolean.toString(autoPing));
        properties.setProperty("gameRtt", Boolean.toString(gameRtt));
        properties.setProperty("fireDot", Boolean.toString(fireDot));
        properties.setProperty("drawLink", Boolean.toString(drawLink));
        properties.setProperty("serverShadow", Boolean.toString(serverShadow));
        properties.setProperty("zombiesOnly", Boolean.toString(zombiesOnly));
        properties.setProperty("minDist", Integer.toString(clamp(minDist, 5, 30)));
        properties.setProperty("maxGhosts", Integer.toString(clamp(maxGhosts, 1, 8)));
        properties.setProperty("extraMs", Integer.toString(clamp(extraMs, -100, 400)));
        properties.setProperty("manualPing", Integer.toString(clamp(manualPing, 0, 600)));
        properties.setProperty("markerOffsetX", Integer.toString(clamp(markerOffsetX, -2_000, 2_000)));
        properties.setProperty("markerOffsetY", Integer.toString(clamp(markerOffsetY, -2_000, 2_000)));
        properties.setProperty("markerScaleX", Float.toString(HudLayoutMath.clampScale(markerScaleX)));
        properties.setProperty("markerScaleY", Float.toString(HudLayoutMath.clampScale(markerScaleY)));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("aim-lead.properties"), properties,
                    "MICx AimLead configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save AimLead configuration", exception);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float parseScale(String value) {
        try {
            return HudLayoutMath.clampScale(Float.parseFloat(value));
        } catch (RuntimeException ignored) {
            return 1.0f;
        }
    }
}
