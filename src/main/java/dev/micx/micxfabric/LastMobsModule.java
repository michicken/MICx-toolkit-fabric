package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/**
 * LastMobs（Forge 移植）：回合剩余怪 ≤ N 时，从屏幕准心向每只残怪拉黄色 2D 线，
 * 指示"这回合还差哪几只、在哪个方向"。数据源为计分板 Zombies Left（服务器权威）。
 * 目标：全部敌对怪（Enemy）+ 铁傀儡/狼，排除凋零。投影用相机基向量数学
 * （{@link LastMobsRules#project}），HUD 阶段画旋转细矩形当线。
 */
public final class LastMobsModule implements Module {
    private static final LastMobsModule INSTANCE = new LastMobsModule();
    private static final int DEFAULT_MAX_COUNT = 5;
    private static final int DEFAULT_ALPHA_PCT = 90;
    /** 线色：亮黄 #FFD926（与 ESP 红框、SlimeForecast 绿 X 区分）。 */
    private static final int LINE_RGB = 0xFFD926;
    /** 屏幕边缘夹取余量（px）：屏幕外怪仍给出方向指示。 */
    private static final int SCREEN_MARGIN = 4;

    private boolean enabled;
    private int maxCount = DEFAULT_MAX_COUNT;
    private int lineAlphaPct = DEFAULT_ALPHA_PCT;
    private boolean configLoaded;

    private LastMobsModule() {
    }

    public static LastMobsModule instance() {
        return INSTANCE;
    }

    @Override public String id() { return "last_mobs"; }
    @Override public boolean defaultEnabled() { return true; }
    @Override public boolean enabled() { return enabled; }

    @Override public void setEnabled(boolean v) {
        loadConfig();
        enabled = v;
        ModuleStateStore.put(id(), v);
    }

    /** 开关组合键（最多 3 键）：默认空绑定。 */
    private int[] toggleKeyCodes = KeyChord.EMPTY;

    @Override public int[] primaryChord() { loadConfig(); return toggleKeyCodes; }

    public void setToggleKeyCodes(int[] codes) {
        toggleKeyCodes = KeyChord.normalize(codes);
        saveConfig();
    }

    @Override public void onPrimaryPressed(net.minecraft.client.Minecraft client, boolean newlyEnabled) {
        if (!newlyEnabled) setEnabled(false);
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(dev.micx.micxfabric.ChatMessageStyles.notice(
                    enabled() ? "LastMobs 开启：剩余 ≤" + getMaxCount() + " 只时准心拉线" : "LastMobs 关闭"));
        }
    }

    public int getMaxCount() { loadConfig(); return maxCount; }
    public void setMaxCount(int n) { maxCount = Math.max(1, Math.min(10, n)); saveConfig(); }
    public int getLineAlphaPct() { loadConfig(); return lineAlphaPct; }
    public void setLineAlphaPct(int pct) { lineAlphaPct = Math.max(20, Math.min(100, pct)); saveConfig(); }

    @Override public void tick(Minecraft client) {
    }

    @Override public void resetState() {
    }

    public void drawHud(GuiGraphicsExtractor g) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;
        ZombiesTracker tracker = ZombiesTracker.instance();
        if (!tracker.isInZombies()) return;
        if (!LastMobsRules.isActive(tracker.zombiesLeft(), getMaxCount())) return;

        int width = g.guiWidth();
        int height = g.guiHeight();
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        float fovY = mc.options.fov().get().floatValue();
        double aspect = mc.getWindow() != null && mc.getWindow().getHeight() > 0
                ? (double) mc.getWindow().getWidth() / mc.getWindow().getHeight()
                : 16.0 / 9.0;

        List<LivingEntity> mobs = collectTargets(mc.level, mc);
        if (mobs.isEmpty()) return;
        // 防御裁剪：场景怪数可能多于 zombiesLeft（侧边栏刷新延迟），按距离取最近 N 只
        Vec3 self = mc.player.position();
        mobs.sort(Comparator.comparingDouble(m -> m.distanceToSqr(self)));
        int n = Math.min(getMaxCount(), mobs.size());

        float cx = width * 0.5f;
        float cy = height * 0.5f;
        int argb = (Math.round(lineAlphaPct * 2.55f) & 255) << 24 | LINE_RGB;
        for (int i = 0; i < n; i++) {
            LivingEntity mob = mobs.get(i);
            Vec3 center = mob.position().add(0.0D, mob.getBbHeight() * 0.5D, 0.0D);
            float[] screenPoint = LastMobsRules.project(
                    center.x - cam.x, center.y - cam.y, center.z - cam.z,
                    yaw, pitch, fovY, aspect, width, height);
            if (screenPoint == null) continue;   // 相机背后不画
            float[] clamped = LastMobsRules.clampToScreen(screenPoint[0], screenPoint[1],
                    width, height, SCREEN_MARGIN);
            drawLine(g, cx, cy, clamped[0], clamped[1], argb);
        }
    }

    /**
     * 残怪目标：Enemy 接口（僵尸/小丑/史莱姆/巨人等）+ 铁傀儡 + 狼；
     * 排除凋零（AA 准心凋零机制实体不画）、玩家、盔甲架与已死实体。
     */
    private static List<LivingEntity> collectTargets(ClientLevel level, Minecraft mc) {
        List<LivingEntity> result = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living == mc.player || !living.isAlive()) continue;
            if (living instanceof ArmorStand || living instanceof net.minecraft.world.entity.player.Player) continue;
            String typePath = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).getPath();
            if ("wither".equals(typePath)) continue;
            if (!(living instanceof Enemy)
                    && !"iron_golem".equals(typePath)
                    && !"wolf".equals(typePath)) continue;
            result.add(living);
        }
        return result;
    }

    /** 从准心中心向目标点画一条 2px 旋转细矩形（GuiGraphics 无画线 API）。 */
    private static void drawLine(GuiGraphicsExtractor g, float cx, float cy,
                                 float x, float y, int argb) {
        float dx = x - cx;
        float dy = y - cy;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0f) return;
        float angle = (float) Math.atan2(dy, dx);
        g.pose().pushMatrix();
        try {
            g.pose().translate(cx, cy);
            g.pose().rotate(angle);
            g.fill(0, -1, Math.round(length), 1, argb);
        } finally {
            g.pose().popMatrix();
        }
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path cur = FabricRuntime.configPath().resolve("last-mobs.properties");
        Path leg = FabricRuntime.configPath().getParent().resolve("MICxToolkit_LastMobs.cfg");
        Properties p = ConfigProperties.load(cur, leg);
        maxCount = ConfigProperties.integer(p, "maxCount", DEFAULT_MAX_COUNT, 1, 10);
        lineAlphaPct = ConfigProperties.integer(p, "lineAlphaPct", DEFAULT_ALPHA_PCT, 20, 100);
        toggleKeyCodes = KeyChord.readConfig(p, "toggleKeys", "toggleKey", 0);
    }

    private void saveConfig() {
        Properties p = new Properties();
        p.setProperty("maxCount", Integer.toString(maxCount));
        p.setProperty("lineAlphaPct", Integer.toString(lineAlphaPct));
        KeyChord.writeConfig(p, "toggleKeys", "toggleKey", toggleKeyCodes);
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("last-mobs.properties"), p, "MICx LastMobs");
        } catch (IOException e) {
            MicxFabric.LOGGER.warn("Unable to save LastMobs configuration", e);
        }
    }
}
