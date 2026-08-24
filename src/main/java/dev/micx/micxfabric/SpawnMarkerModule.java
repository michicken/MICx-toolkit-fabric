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
 * AA 刷怪点标记（Forge SpawnMarkerModule 移植，v2.18.10 定稿口径）：
 * 模块开启即固定渲染预设刷怪点——11 个地面点灰色光柱（竖线 + 顶部 T 横线，
 * 高 2 格）+ 4 个 UFO 放怪口（y=105 飞船上方）。动态运动确认通道不移植
 * （Forge 端实测动态点随回合单调增长至 239 根，用户定稿纯预设渲染）。
 */
public final class SpawnMarkerModule implements Module {
    private static final SpawnMarkerModule INSTANCE = new SpawnMarkerModule();
    /** 光柱高度：怪物身高两格。 */
    private static final double MARKER_HEIGHT = 2.0;
    /** 顶部 T 形横线半长。 */
    private static final double MARKER_HALF = 0.3;
    /** 灰色（与 ESP 红框 / SlimeForecast 绿 X 区分）。 */
    private static final int GRAY_RGB = 0xA6A6A6;

    /**
     * AA 已知刷怪点 {x, y, z}（ZombiesLogger 3.5.0 实测聚类）：
     * p1~p5 传送点 + 后期角落区；UFO 口在 y=105 飞船正上方。
     */
    private static final double[][] PRESET_SPAWNS = {
            {   6.0, 72.0,  32.0 },   // p1 传送点
            { -22.0, 72.0,  16.0 },   // p2 传送点
            {  22.0, 72.0,  14.0 },   // p5 传送点
            { -22.0, 72.0,  10.0 },   // p3 传送点
            { -10.0, 72.0,  -6.0 },   // p4 传送点
            {  28.0, 72.0,  32.0 },   // ult 终极机器角
            { -28.0, 72.0,  28.0 },   // rc_back
            {  18.0, 72.0,  44.0 },   // alt 摩天轮角
            { -12.0, 72.0,  40.0 },   // cc 宝箱角
            {  22.0, 72.0, -14.0 },   // perk
            {  34.0, 72.0,  -2.0 },   // bc_ent 宝箱入口
            {  -2.0, 105.0, 12.0 },   // UFO 口1
            {  -2.0, 105.0, 14.0 },   // UFO 口2
            {   2.0, 105.0, 12.0 },   // UFO 口3
            {   2.0, 105.0, 14.0 },   // UFO 口4
    };

    public int presetCount() {
        return PRESET_SPAWNS.length;
    }

    private boolean enabled;
    private float alpha = 0.6f;
    private boolean configLoaded;

    private SpawnMarkerModule() {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::collectSubmits);
    }

    public static SpawnMarkerModule instance() {
        return INSTANCE;
    }

    @Override public String id() { return "spawn_marker"; }
    @Override public boolean defaultEnabled() { return false; }
    @Override public boolean enabled() { return enabled; }

    @Override public void setEnabled(boolean v) {
        loadConfig();
        enabled = v;
        ModuleStateStore.put(id(), v);
    }

    public float getAlpha() { loadConfig(); return alpha; }
    public void setAlpha(float a) { alpha = Math.max(0.05f, Math.min(1.0f, a)); saveConfig(); }

    private void collectSubmits(LevelRenderContext ctx) {
        if (!enabled || !ZombiesTracker.instance().isInAlienArcadium()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;
        SubmitNodeCollector collector = ctx.submitNodeCollector();
        PoseStack pose = ctx.poseStack();
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        int argb = (Math.round(alpha * 255) & 255) << 24 | GRAY_RGB;
        double[] segments = new double[PRESET_SPAWNS.length * 12];
        for (int i = 0; i < PRESET_SPAWNS.length; i++) {
            WorldLines.appendColumn(segments, i * 12,
                    PRESET_SPAWNS[i][0] - cam.x, PRESET_SPAWNS[i][1] - cam.y, PRESET_SPAWNS[i][2] - cam.z,
                    MARKER_HEIGHT, MARKER_HALF, argb);
        }
        WorldLines.submit(collector, pose, EspRenderTypes.espLines(), segments, argb);
    }

    @Override public void resetState() {}

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path cur = FabricRuntime.configPath().resolve("spawn-marker.properties");
        Path leg = FabricRuntime.configPath().getParent().resolve("MICxToolkit_SpawnMarker.cfg");
        Properties p = ConfigProperties.load(cur, leg);
        String a = p.getProperty("alphaPct");
        if (a != null) try { alpha = Math.max(0.05f, Math.min(1.0f, Integer.parseInt(a.trim()) / 100f)); } catch (Exception ignored) {}
    }

    private void saveConfig() {
        Properties p = new Properties();
        p.setProperty("alphaPct", Integer.toString(Math.round(alpha * 100)));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("spawn-marker.properties"), p, "MICx SpawnMarker");
        } catch (IOException e) {
            MicxFabric.LOGGER.warn("Unable to save SpawnMarker configuration", e);
        }
    }
}
