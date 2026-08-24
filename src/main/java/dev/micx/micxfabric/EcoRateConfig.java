package dev.micx.micxfabric;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/** EcoRate flashing rhythm — interval/duration in seconds. */
public final class EcoRateConfig {
    public int flashIntervalSec = 5;
    public int flashDurationSec = 2;

    private boolean loaded;

    public void load() {
        if (loaded) return;
        loaded = true;
        Path current = FabricRuntime.configPath().resolve("eco-rate.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_EcoRate.cfg");
        Properties p = ConfigProperties.load(current, legacy);
        flashIntervalSec = clampInterval(ConfigProperties.integer(p, "flashIntervalSec", 5, 2, 30));
        flashDurationSec = clampDuration(ConfigProperties.integer(p, "flashDurationSec", 2, 1, 10));
    }

    public void save() {
        flashIntervalSec = clampInterval(flashIntervalSec);
        flashDurationSec = clampDuration(flashDurationSec);
        Properties p = new Properties();
        p.setProperty("flashIntervalSec", Integer.toString(flashIntervalSec));
        p.setProperty("flashDurationSec", Integer.toString(flashDurationSec));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("eco-rate.properties"), p, "MICx EcoRate configuration");
        } catch (IOException e) {
            MicxFabric.LOGGER.warn("Unable to save EcoRate configuration", e);
        }
    }

    private int clampInterval(int v) {
        return Math.max(2, Math.min(30, v));
    }

    private int clampDuration(int v) {
        v = Math.max(1, Math.min(10, v));
        return Math.min(v, flashIntervalSec - 1);
    }
}
