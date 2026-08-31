package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/** Hides or marks nearby players while preserving self and sleeping-player visibility. */
public final class PlayerVisibilityModule implements Module {
    private static final PlayerVisibilityModule INSTANCE = new PlayerVisibilityModule();
    private static final int DEFAULT_KEY = GLFW.GLFW_KEY_G;
    private boolean enabled;
    private boolean active = true;
    private boolean hideMode;
    private float opacity = 0.15f;
    private float range = 4.5f;
    private int keyCode = DEFAULT_KEY;
    private boolean configLoaded;

    private PlayerVisibilityModule() {
    }

    public static PlayerVisibilityModule instance() {
        return INSTANCE;
    }

    public static boolean shouldHide(AbstractClientPlayer target, Minecraft client) {
        diagState(client);
        boolean result = INSTANCE.enabled && INSTANCE.active && INSTANCE.hideMode && INSTANCE.inRange(target, client);
        if (result) diagQualified(target);
        return result;
    }

    /* ---- 排障诊断（hide 不生效定位用，3s 节流；定位后可移除） ---- */

    private static long lastStateDiagMs;
    private static long lastCancelDiagMs;
    private static long lastQualifiedDiagMs;

    /** 每 3s 一行当前开关/范围状态（shouldHide 每帧调用，内部节流）。
     *  near= 距离达标人数（未排除睡觉）；inRange= 排除睡觉后人数——两者差值大=贴的是倒地队友。 */
    public static void diagState(Minecraft client) {
        long now = System.currentTimeMillis();
        if (now - lastStateDiagMs < 3000L) return;
        lastStateDiagMs = now;
        int near = 0;
        int inRange = 0;
        if (client != null && client.level != null && client.player != null) {
            for (AbstractClientPlayer p : client.level.players()) {
                if (p == client.player) continue;
                double dx = client.player.getX() - p.getX();
                double dz = client.player.getZ() - p.getZ();
                if (dx * dx + dz * dz > INSTANCE.range * INSTANCE.range) continue;
                near++;
                if (INSTANCE.inRange(p, client)) inRange++;
            }
        }
        MicxFabric.LOGGER.info("[micx-pv] state: enabled={} active={} hideMode={} opacity={} range={} near={} inRange={}",
                INSTANCE.enabled, INSTANCE.active, INSTANCE.hideMode, INSTANCE.opacity, INSTANCE.range, near, inRange);
    }

    /** shouldHide 判定真·通过行（区分"条件没满足"vs"钩子没命中"）。 */
    static void diagQualified(AbstractClientPlayer player) {
        long now = System.currentTimeMillis();
        if (now - lastQualifiedDiagMs < 3000L) return;
        lastQualifiedDiagMs = now;
        MicxFabric.LOGGER.info("[micx-pv] hide-qualified: {}", player.getName().getString());
    }

    /** 取消渲染命中行（两个拦截点各打，3s 节流）。 */
    public static void diagCancel(String site, AbstractClientPlayer player) {
        long now = System.currentTimeMillis();
        if (now - lastCancelDiagMs < 3000L) return;
        lastCancelDiagMs = now;
        MicxFabric.LOGGER.info("[micx-pv] {}-cancel: {}", site, player.getName().getString());
    }

    public static int modelTint(AbstractClientPlayer target, Minecraft client) {
        if (!INSTANCE.enabled || !INSTANCE.active || INSTANCE.hideMode || !INSTANCE.inRange(target, client)) return -1;
        int alpha = Math.max(1, Math.min(255, Math.round(INSTANCE.opacity * 255.0f)));
        return (alpha << 24) | 0x00FFFFFF;
    }

    private boolean inRange(AbstractClientPlayer target, Minecraft client) {
        if (target == null || client == null || client.player == null || target == client.player
                || target.isRemoved()) return false;
        // 倒地（睡觉）玩家永远保持可见：isSleeping 与 Pose.SLEEPING 双保险——
        // 1.8.9 尸体经 Via 转换后 isSleeping 标记可能丢失（与 ReviveAura 判定同款）
        if (target.isSleeping() || target.getPose() == net.minecraft.world.entity.Pose.SLEEPING) return false;
        // 对齐 Forge：水平(XZ)距离判定，双方不同高度（站在台阶/架子上）也算在范围内
        double dx = client.player.getX() - target.getX();
        double dz = client.player.getZ() - target.getZ();
        return dx * dx + dz * dz <= range * range;
    }

    @Override
    public String id() {
        return "player_visibility";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        loadConfig();
        this.enabled = enabled;
        // 开模块即武装 active：消除"enabled/active/hideMode 三开关少一个就静默失效"的死角
        active = enabled;
        ModuleStateStore.put(id(), enabled);
    }

    @Override
    public InputBinding primaryBinding() {
        loadConfig();
        return new InputBinding(keyCode);
    }

    @Override
    public void onPrimaryPressed(Minecraft client, boolean newlyEnabled) {
        if (newlyEnabled) active = true;
        else active = !active;
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(ChatMessageStyles.notice("PlayerVisibility: " + (active ? "ON" : "OFF")));
        }
        saveConfig();
    }

    public boolean active() {
        return active;
    }

    public boolean hideMode() {
        return hideMode;
    }

    public float opacity() {
        return opacity;
    }

    public float range() {
        return range;
    }

    public int keyCode() {
        loadConfig();
        return keyCode;
    }

    public void setActive(boolean value) {
        active = value;
        saveConfig();
    }

    public void setHideMode(boolean value) {
        hideMode = value;
        // 切到 hide 模式时自动武装 active（同上：模式切换不该被另一个静默开关卡死）
        if (value && enabled && !active) {
            active = true;
            MicxFabric.LOGGER.info("[micx-pv] hide mode armed active=true");
        }
        saveConfig();
    }

    public void setOpacity(float value) {
        opacity = Math.max(0.05f, Math.min(1.0f, value));
        saveConfig();
    }

    public void setRange(float value) {
        range = Math.max(0.5f, Math.min(64.0f, value));
        saveConfig();
    }

    public void setKeyCode(int value) {
        keyCode = value == 0 ? GLFW.GLFW_KEY_UNKNOWN : Math.max(-108, Math.min(GLFW.GLFW_KEY_LAST, value));
        saveConfig();
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path current = FabricRuntime.configPath().resolve("player-visibility.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_PlayerVisibility.cfg");
        Properties properties = ConfigProperties.load(current, legacy);
        active = ConfigProperties.bool(properties, "active", true);
        hideMode = ConfigProperties.bool(properties, "hideMode", false);
        opacity = parseFloat(properties.getProperty("opacity"), 0.15f, 0.05f, 1.0f);
        float rangeSq = parseFloat(properties.getProperty("rangeSq"), -1f, -1f, 4096f);
        if (rangeSq < 0f || Math.abs(rangeSq - 2.0f) < 0.0001f) {
            // 新装 + 旧默认(1.41²) 一起迁到 4.5²——1.41 格=必须贴脸，实测体感"完全不生效"的主因
            rangeSq = 20.25f;
        }
        range = (float) Math.sqrt(rangeSq);
        keyCode = ConfigProperties.integer(properties, "keyCode", DEFAULT_KEY, -108, GLFW.GLFW_KEY_LAST);
    }

    private void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("active", Boolean.toString(active));
        properties.setProperty("hideMode", Boolean.toString(hideMode));
        properties.setProperty("opacity", Float.toString(opacity));
        properties.setProperty("rangeSq", Float.toString(range * range));
        properties.setProperty("keyCode", Integer.toString(keyCode));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("player-visibility.properties"), properties,
                    "MICx PlayerVisibility configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save PlayerVisibility configuration", exception);
        }
    }

    private static float parseFloat(String value, float fallback, float min, float max) {
        try {
            return Math.max(min, Math.min(max, Float.parseFloat(value)));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
