package dev.micx.micxfabric;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 1.8 潜行视觉：仅抬相机/模型视觉高度，不改碰撞（1.5 格缝照样能钻）。
 *
 * <p>26.2 CROUCHING eyeHeight=1.27（STANDING 1.62），落差 0.35；1.8 潜行只降 0.08。
 * 本模块在逻辑保持 CROUCHING 碰撞的前提下，视觉上把潜行眼高抬回 1.54（即 +0.27）。
 */
public final class LegacySneakVisualsModule implements Module {
    private static final LegacySneakVisualsModule INSTANCE = new LegacySneakVisualsModule();
    private boolean enabled;
    private boolean configLoaded;

    /** 潜行视觉补偿：现代潜行 1.27 → 1.8 潜行 1.54。 */
    public static final float VISUAL_SNEAK_EYE_HEIGHT = 1.54f;
    /** 现代潜行碰撞对应的 eyeHeight（源真值，用于判定“是否处于现代潜行眼高”）。 */
    public static final float MODERN_SNEAK_EYE_HEIGHT = 1.27f;
    /** 抬升量。 */
    public static final float DELTA_Y = VISUAL_SNEAK_EYE_HEIGHT - MODERN_SNEAK_EYE_HEIGHT; // 0.27

    private LegacySneakVisualsModule() {}

    public static LegacySneakVisualsModule instance() { return INSTANCE; }

    @Override public String id() { return "legacy_sneak_visuals"; }
    @Override public boolean defaultEnabled() { return false; }
    @Override public boolean enabled() { return enabled; }

    @Override public void setEnabled(boolean v) {
        loadConfig();
        enabled = v;
        ModuleStateStore.put(id(), v);
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
    }

    private void saveConfig() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("legacy-sneak-visuals.properties"), p,
                    "MICx LegacySneakVisuals");
        } catch (IOException e) {
            MicxFabric.LOGGER.warn("Unable to save LegacySneakVisuals configuration", e);
        }
    }
}
