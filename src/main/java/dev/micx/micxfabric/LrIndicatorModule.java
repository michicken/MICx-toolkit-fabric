package dev.micx.micxfabric;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public final class LrIndicatorModule implements Module {
    private static final LrIndicatorModule INSTANCE = new LrIndicatorModule();
    private static volatile LrIndicatorModule ACTIVE;
    private static final float RING_RADIUS = 6.0f;
    private static final int DOT_GAP = 6;
    private static final int DOT_Y_ABOVE_HOTBAR = 80;
    private static final int COLOR_NUM = 0xFFFFFFFF;
    private static final int COLOR_GREEN = 0xFF55FF55;
    private static final int COLOR_RED = 0xFFFF5555;
    private static final long BEEP_GAP_MS = 200L;

    private final LrIndicatorState state = new LrIndicatorState();
    private boolean enabled;
    private boolean configLoaded;
    public int lrRotationPosition = 1;
    public boolean lrBeepEnabled = true;
    public float lrBeepVolume = 1.0f;
    public int lrHudDx = 0;
    public int lrHudDy = 0;
    private final ArrayDeque<Long> pendingBeeps = new ArrayDeque<>();

    private LrIndicatorModule() {}

    public static LrIndicatorModule instance() { return INSTANCE; }

    @Override public String id() { return "lr_indicator"; }
    @Override public boolean defaultEnabled() { return true; }
    @Override public boolean enabled() { return enabled; }
    @Override public void setEnabled(boolean v) {
        loadConfig();
        enabled = v;
        if (!v) resetState();
        ModuleStateStore.put(id(), v);
    }

    public int lrRotationPosition() { loadConfig(); return lrRotationPosition; }
    public boolean lrBeepEnabled() { loadConfig(); return lrBeepEnabled; }

    public void setRotationPosition(int pos) {
        loadConfig();
        lrRotationPosition = Math.max(1, Math.min(4, pos));
        saveConfig();
    }

    /** HUD Layout 编辑器拖动时设置偏移（相对快捷栏上方的默认位置）。 */
    public void setHudOffsets(int dx, int dy) {
        loadConfig();
        lrHudDx = Math.max(-300, Math.min(300, dx));
        lrHudDy = Math.max(-300, Math.min(300, dy));
        saveConfig();
    }

    public static void onRoundChanged(int round) {
        LrIndicatorModule m = ACTIVE;
        if (m == null || !m.enabled) return;
        m.state.onRoundChanged(round);
    }

    public void onGolemJoin(long now, double x, double y, double z) {
        if (!enabled) return;
        state.onGolemJoin(now, x, y, z);
    }

    /**
     * 最近 {@code windowMs} 内是否释放过 LR——无敌怪判定的门控信号（用户定稿 2026-09-16：
     * 只有 LR 会造成无敌怪，所以没有 LR 窗口时不判定）。
     *
     * <p>模块关闭时返回 false（释放时刻由本模块记录，关掉就没人记了）。
     */
    public static boolean lrReleasedWithin(long nowMs, long windowMs) {
        LrIndicatorModule m = INSTANCE;
        return m.enabled && m.state.releasedWithin(nowMs, windowMs);
    }

    public void onSound(String soundId, float pitch, double x, double y, double z, long now) {
        if (!enabled || soundId == null) return;
        String id = soundId.toLowerCase(java.util.Locale.ROOT);
        if (id.startsWith("minecraft:")) id = id.substring("minecraft:".length());
        if (!"ambient.weather.thunder".equals(id) && !"entity.lightning_bolt.thunder".equals(id)) return;
        ZombiesTracker zt = ZombiesTracker.instance();
        if (zt == null || !zt.isInAlienArcadium()) return;
        if (!LrIndicatorState.isLrPitch(pitch)) return;
        if (state.isGolemThunder(now, (float) x, (float) y, (float) z)) return;
        if (state.isBossFirstThunder(zt.round())) return;
        if (state.tryRelease(now)) onValidRelease(now);
    }

    /**
     * 最后一次有效释放的时刻（只增不改）——KeyboardClicker 混合换弹联动用（0.2.112）：
     * 释放记录 18s 会被剪枝，长回合回头查不到，所以留一个永不过期的时刻，
     * 调用方比「时刻 ≥ 本回合开始时刻」即得「本回合有没有人放 LR」。
     * 只在模块开启时更新（关掉没人记录，与 {@link #lrReleasedWithin} 同口径）。
     */
    private static volatile long lastReleaseMs;

    public static long lrLastReleaseMs() {
        return lastReleaseMs;
    }

    private void onValidRelease(long releaseAtMs) {
        lastReleaseMs = releaseAtMs;
        if (!lrBeepEnabled) return;
        int pos = lrRotationPosition;
        int beeps = 1;
        if (pos >= 2 && pos <= 4 && state.rotationCount() % 4 == pos - 1) beeps = 5;
        scheduleBeep(beeps);
    }

    private void scheduleBeep(int count) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < count; i++) pendingBeeps.addLast(now + i * BEEP_GAP_MS);
    }

    private void playBeepNow() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getSoundManager() == null) return;
            loadConfig();
            int times = Math.max(1, Math.round(lrBeepVolume));
            for (int i = 0; i < times; i++) {
                var ev = SoundEvents.NOTE_BLOCK_PLING.value();
                var inst = new SimpleSoundInstance(ev, SoundSource.MASTER, 1.0f, 1.0f, RandomSource.create(), 0, 0, 0);
                mc.getSoundManager().play(inst);
            }
        } catch (Throwable ignored) {}
    }

    @Override public void tick(Minecraft client) {
        if (!enabled) return;
        state.greenCount(System.currentTimeMillis());
        if (!pendingBeeps.isEmpty()) {
            long now = System.currentTimeMillis();
            while (!pendingBeeps.isEmpty() && pendingBeeps.peekFirst() <= now) {
                pendingBeeps.removeFirst();
                playBeepNow();
            }
        }
    }

    @Override public void resetState() { state.reset(); pendingBeeps.clear(); }

    public void drawHud(net.minecraft.client.gui.GuiGraphicsExtractor g) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;
        ZombiesTracker zt = ZombiesTracker.instance();
        if (zt == null || !zt.isInAlienArcadium()) return;
        int players = mc.level.players().size();
        if (players <= 0) return;
        state.setMaxPlayers(players);
        long now = System.currentTimeMillis();
        int green = state.greenCount(now);
        if (state.shouldHide(now, green)) return;
        loadConfig();
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        int cy = sh - 22 - DOT_Y_ABOVE_HOTBAR + lrHudDy;
        int totalW = (int) (players * 2 * RING_RADIUS + (players - 1) * DOT_GAP);
        int cx = sw / 2 - totalW / 2 + (int) RING_RADIUS + lrHudDx;
        float scale = 1.0f;
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        try {
            for (int i = 0; i < players; i++) {
                int color = i < green ? COLOR_GREEN : COLOR_RED;
                boolean isGreen = i < green;
                String label = String.valueOf(i + 1);
                int bg = isGreen ? 0xFF2E7D32 : 0xFFB71C1C;
                int w = mc.font.width(label) + 6;
                int h = 10;
                int x0 = cx - w / 2;
                int y0 = cy - h / 2;
                g.fill(x0, y0, x0 + w, y0 + h, bg);
                g.text(mc.font, net.minecraft.network.chat.Component.literal(label), x0 + 3, y0 + 1, COLOR_NUM, true);
                cx += 2 * RING_RADIUS + DOT_GAP;
            }
        } finally { g.pose().popMatrix(); }
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        ACTIVE = this;
        Path cur = FabricRuntime.configPath().resolve("lr-indicator.properties");
        Path leg = FabricRuntime.configPath().getParent().resolve("MICxToolkit_LrIndicator.cfg");
        Properties p = ConfigProperties.load(cur, leg);
        lrHudDx = ConfigProperties.integer(p, "lrHudDx", 0, -2000, 2000);
        lrHudDy = ConfigProperties.integer(p, "lrHudDy", 0, -2000, 2000);
        lrRotationPosition = ConfigProperties.integer(p, "lrRotationPosition", 1, 1, 4);
        lrBeepEnabled = ConfigProperties.bool(p, "lrBeepEnabled", true);
        String vol = p.getProperty("lrBeepVolume");
        if (vol != null) try { lrBeepVolume = Math.max(0.1f, Math.min(5.0f, Float.parseFloat(vol.trim()))); } catch (Exception ignored) {}
    }

    private void saveConfig() {
        Properties p = new Properties();
        p.setProperty("lrHudDx", Integer.toString(lrHudDx));
        p.setProperty("lrHudDy", Integer.toString(lrHudDy));
        p.setProperty("lrRotationPosition", Integer.toString(lrRotationPosition));
        p.setProperty("lrBeepEnabled", Boolean.toString(lrBeepEnabled));
        p.setProperty("lrBeepVolume", Float.toString(lrBeepVolume));
        try { AtomicProperties.store(FabricRuntime.configPath().resolve("lr-indicator.properties"), p, "MICx LrIndicator"); } catch (IOException e) { MicxFabric.LOGGER.warn("Unable to save LrIndicator configuration", e); }
    }
}
