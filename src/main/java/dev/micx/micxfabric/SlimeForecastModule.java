package dev.micx.micxfabric;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 史莱姆波预告（Forge SlimeForecastModule 移植）：刷史莱姆/岩浆的波次到来前，
 * 在 12 个固定刷怪点显示贴地 X（墨绿=未来波 → 亮绿=当前波正在刷 → 刷出后
 * 转墨绿/隐藏）。仅 AA 生效；Force 模式无波也常显 12 点。
 */
public final class SlimeForecastModule implements Module {
    private static final SlimeForecastModule INSTANCE = new SlimeForecastModule();
    private static final double X_HALF = 1.0;
    /** 亮绿（本波正在刷）/ 墨绿（未来波预告）。 */
    private static final int BRIGHT_RGB = 0x00FF00;
    private static final int DARK_RGB = 0x007300;

    private final SlimeForecastState state = new SlimeForecastState();
    private Object lastLevel;
    private boolean enabled;
    private float alpha = 0.6f;
    private boolean forceMode;
    private boolean configLoaded;

    private SlimeForecastModule() {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::collectSubmits);
    }

    public static SlimeForecastModule instance() {
        return INSTANCE;
    }

    @Override public String id() { return "slime_forecast"; }
    @Override public boolean defaultEnabled() { return false; }
    @Override public boolean enabled() { return enabled; }

    @Override public void setEnabled(boolean v) {
        loadConfig();
        enabled = v;
        ModuleStateStore.put(id(), v);
        if (!v) state.reset();
    }

    public float getAlpha() { loadConfig(); return alpha; }
    public void setAlpha(float a) { alpha = Math.max(0.05f, Math.min(1.0f, a)); saveConfig(); }
    public boolean isForceMode() { loadConfig(); return forceMode; }

    public void setForceMode(boolean v) {
        forceMode = v;
        saveConfig();
    }

    public SlimeForecastState.Phase phase() {
        return state.phase();
    }

    @Override public void tick(Minecraft client) {
        if (client == null) return;
        if (client.level != lastLevel) {
            state.reset();
            lastLevel = client.level;
            return;
        }
        ZombiesTracker tracker = ZombiesTracker.instance();
        if (!tracker.isInAlienArcadium()) {
            state.reset();
            return;
        }
        long now = System.currentTimeMillis();
        int round = tracker.round();
        long elapsed = Math.max(0L, now - tracker.roundStartMs());
        state.setRound(round, ZombiesWaveSchedule.waveAt(round, elapsed));
    }

    /**
     * 史莱姆/岩浆实体加入世界（MixinClientPacketListener 调用）：新鲜计算当前波次——
     * join 事件在 tick 中段先触发，用 tick 缓存的 wave 会滞后一帧，导致 R10 这类
     * 每波 1 只的回合"刷出后转墨绿/隐藏"整体失效。
     */
    public void onSlimeJoined(long now) {
        if (!enabled) return;
        ZombiesTracker tracker = ZombiesTracker.instance();
        if (!tracker.isInAlienArcadium()) return;
        int round = tracker.round();
        long elapsed = Math.max(0L, now - tracker.roundStartMs());
        state.onSlimeSpawned(ZombiesWaveSchedule.waveAt(round, elapsed));
    }

    @Override public void resetState() {
        state.reset();
        alpha = 0.6f;
        forceMode = false;
        saveConfig();
    }

    private void collectSubmits(LevelRenderContext ctx) {
        if (!enabled || !ZombiesTracker.instance().isInAlienArcadium()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;
        SlimeForecastState.Phase phase = state.phase();
        if (phase == SlimeForecastState.Phase.HIDDEN && forceMode) phase = SlimeForecastState.Phase.DARK;
        if (phase == SlimeForecastState.Phase.HIDDEN) return;

        SubmitNodeCollector collector = ctx.submitNodeCollector();
        PoseStack pose = ctx.poseStack();
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        int rgb = phase == SlimeForecastState.Phase.BRIGHT ? BRIGHT_RGB : DARK_RGB;
        int argb = (Math.round(alpha * 255) & 255) << 24 | rgb;
        double[][] points = SlimeForecastState.slimePoints();
        double[] segments = new double[points.length * 12];
        for (int i = 0; i < points.length; i++) {
            WorldLines.appendCrossX(segments, i * 12,
                    points[i][0] - cam.x, points[i][1] - cam.y, points[i][2] - cam.z,
                    X_HALF, argb);
        }
        WorldLines.submit(collector, pose, EspRenderTypes.espLines(), segments, argb);
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path cur = FabricRuntime.configPath().resolve("slime-forecast.properties");
        Path leg = FabricRuntime.configPath().getParent().resolve("MICxToolkit_SlimeForecast.cfg");
        Properties p = ConfigProperties.load(cur, leg);
        String a = p.getProperty("alphaPct");
        if (a != null) try { alpha = Math.max(0.05f, Math.min(1.0f, Integer.parseInt(a.trim()) / 100f)); } catch (Exception ignored) {}
        String f = p.getProperty("forceMode");
        if (f != null) forceMode = Boolean.parseBoolean(f.trim());
    }

    private void saveConfig() {
        Properties p = new Properties();
        p.setProperty("alphaPct", Integer.toString(Math.round(alpha * 100)));
        p.setProperty("forceMode", Boolean.toString(forceMode));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("slime-forecast.properties"), p, "MICx SlimeForecast");
        } catch (IOException e) {
            MicxFabric.LOGGER.warn("Unable to save SlimeForecast configuration", e);
        }
    }
}
