package dev.micx.micxfabric;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

public final class GolemMarkerModule implements Module {
    private static final GolemMarkerModule INSTANCE = new GolemMarkerModule();
    /** 贴地 X 半长（格）。 */
    private static final double X_HALF = 1.0;
    private static final double[][] POINTS = {
            {-24.0, 72.0, 32.0},
            {22.0, 72.0, 32.0},
            {16.0, 72.0, 14.0},
            {6.0, 72.0, -4.0},
            {-10.0, 72.0, 28.0},
    };
    private boolean enabled;
    private float alpha = 0.85f;
    private boolean configLoaded;

    private GolemMarkerModule() {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::collectSubmits);
    }

    public static GolemMarkerModule instance() { return INSTANCE; }

    @Override public String id() { return "golem_marker"; }
    @Override public boolean defaultEnabled() { return true; }
    @Override public boolean enabled() { return enabled; }
    @Override public void setEnabled(boolean v) { loadConfig(); enabled = v; ModuleStateStore.put(id(), v); }
    public float getAlpha() { loadConfig(); return alpha; }
    public void setAlpha(float a) { alpha = Math.max(0.05f, Math.min(1.0f, a)); saveConfig(); }

    private void collectSubmits(LevelRenderContext ctx) {
        if (!enabled || !ZombiesTracker.instance().isInAlienArcadium()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;
        SubmitNodeCollector collector = ctx.submitNodeCollector();
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        int argb = (Math.round(alpha * 255) & 255) << 24 | 0x888888;
        double[] segments = new double[POINTS.length * 12];
        for (int i = 0; i < POINTS.length; i++) {
            WorldLines.appendCrossX(segments, i * 12,
                    POINTS[i][0] - cam.x, POINTS[i][1] - cam.y, POINTS[i][2] - cam.z,
                    X_HALF, argb);
        }
        WorldLines.submit(collector, ctx.poseStack(), EspRenderTypes.espLines(), segments, argb);
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path cur = FabricRuntime.configPath().resolve("golem-marker.properties");
        Path leg = FabricRuntime.configPath().getParent().resolve("MICxToolkit_GolemMarker.cfg");
        Properties p = ConfigProperties.load(cur, leg);
        String v = p.getProperty("alphaPct");
        if (v != null) try { alpha = Math.max(0.05f, Math.min(1.0f, Integer.parseInt(v.trim()) / 100f)); } catch (Exception ignored) {}
    }

    private void saveConfig() {
        Properties p = new Properties();
        p.setProperty("alphaPct", Integer.toString(Math.round(alpha * 100)));
        try { AtomicProperties.store(FabricRuntime.configPath().resolve("golem-marker.properties"), p, "MICx GolemMarker"); } catch (IOException e) { MicxFabric.LOGGER.warn("Unable to save GolemMarker configuration", e); }
    }

    @Override public void resetState() {}
}
