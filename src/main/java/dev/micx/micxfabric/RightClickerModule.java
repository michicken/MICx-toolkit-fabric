package dev.micx.micxfabric;

import com.mojang.blaze3d.platform.InputConstants;
import dev.micx.micxfabric.mixin.KeyMappingAccess;
import dev.micx.micxfabric.mixin.RightClickDelayAccess;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Real-click RightClicker: injects one KeyMapping.click per interval,
 * at most once per game tick, and suppresses vanilla held-use chassis.
 * Default flat 20 CPS (20-20), panel/commands adjustable; yields to SkillCast.
 */
public final class RightClickerModule implements Module {
    private static final RightClickerModule INSTANCE = new RightClickerModule();
    private static final int DEFAULT_KEY = -98;
    private static final int DEFAULT_MIN_CPS = 20;
    private static final int DEFAULT_MAX_CPS = 20;
    private static final int CPS_LIMIT = 50;
    private boolean enabled;
    private boolean active = true;
    private InputBinding binding = new InputBinding(DEFAULT_KEY);
    private int minCps = DEFAULT_MIN_CPS;
    private int maxCps = DEFAULT_MAX_CPS;
    private long lastClickMs;
    private long nextDelayMs;
    private long tickCounter;
    private long lastInjectTick = -1L;
    private boolean configLoaded;

    private RightClickerModule() {
    }

    public static RightClickerModule instance() {
        return INSTANCE;
    }

    public static boolean isActive() {
        return INSTANCE.enabled && INSTANCE.active;
    }

    @Override
    public String id() {
        return "right_clicker";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        loadConfig();
        boolean wasEnabled = this.enabled;
        this.enabled = enabled;
        if (enabled && !wasEnabled) active = true;
        if (!enabled) active = false;
        if (enabled) {
            tickCounter = 0L;
            lastInjectTick = -1L;
        }
        ModuleStateStore.put(id(), enabled);
    }

    @Override
    public InputBinding primaryBinding() {
        loadConfig();
        return binding;
    }

    @Override
    public void onPrimaryPressed(Minecraft client, boolean newlyEnabled) {
        if (newlyEnabled) active = true;
        else active = !active;
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(ChatMessageStyles.notice("RightClicker: " + (active ? "ON" : "OFF")
                    + " CPS " + minCps + "-" + maxCps));
        }
    }

    @Override
    public void resetInput() {
        active = false;
    }

    @Override
    public void resetState() {
        active = true;
        tickCounter = 0L;
        lastInjectTick = -1L;
        lastClickMs = 0L;
        nextDelayMs = 0L;
    }

    @Override
    public void tick(Minecraft client) {
        tickCounter++;
        if (!shouldFire(client)) {
            if (client == null || client.options == null || client.options.keyUse == null
                    || !client.options.keyUse.isDown()) {
                lastClickMs = 0L;
                nextDelayMs = 0L;
            }
            return;
        }
        suppressVanilla(client);
        long now = System.currentTimeMillis();
        if (lastClickMs != 0L && (lastInjectTick == tickCounter || now - lastClickMs < nextDelayMs)) return;
        InputConstants.Key key = resolveUseKey(client.options.keyUse);
        if (key == null) return;
        KeyMapping.click(key);
        lastInjectTick = tickCounter;
        lastClickMs = now;
        nextDelayMs = randomClickDelayMs(minCps, maxCps);
    }

    public boolean active() {
        return active;
    }

    public long tickCounter() {
        return tickCounter;
    }

    public long lastInjectTick() {
        return lastInjectTick;
    }

    public int keyCode() {
        loadConfig();
        return binding.code();
    }

    public void setKeyCode(int code) {
        loadConfig();
        binding.setCode(code == 0 ? GLFW.GLFW_KEY_UNKNOWN : Math.max(-108, Math.min(GLFW.GLFW_KEY_LAST, code)));
        saveConfig();
    }

    public int getMinCps() {
        loadConfig();
        return minCps;
    }

    public int getMaxCps() {
        loadConfig();
        return maxCps;
    }

    public void setCpsRange(int min, int max) {
        loadConfig();
        int a = clampCps(min), b = clampCps(max);
        if (a > b) { int t = a; a = b; b = t; }
        minCps = a;
        maxCps = b;
        saveConfig();
    }

    public static int randomClickDelayMs(int minCps, int maxCps) {
        int lo = 1000 / clampCps(maxCps);
        int hi = Math.max(lo, 1000 / clampCps(minCps));
        return lo + ThreadLocalRandom.current().nextInt(hi - lo + 1);
    }

    public boolean isSupplyingFire() {
        Minecraft client = Minecraft.getInstance();
        return shouldFire(client);
    }

    /**
     * 连点该点哪个键：「使用键」当前绑定的那个键，未绑定返回 null。
     *
     * <p>不能用 {@code getDefaultKey()}：玩家把左右键互换后，默认右键在 {@code KeyMapping.MAP}
     * 里已经属于 keyAttack，点它会打出平 A —— 连点变成 20 CPS 攻击（本次修的 bug）。
     * 点当前绑定才等价于玩家自己按使用键，绑到键盘键时同样成立。
     */
    public static InputConstants.Key resolveUseKey(KeyMapping keyUse) {
        if (keyUse == null || !UseClickRules.shouldInject(keyUse.isUnbound())) return null;
        return currentUseKey(keyUse);
    }

    /** 读 protected 的当前绑定字段；accessor 未生效时退回默认键（正常构建走不到）。 */
    public static InputConstants.Key currentUseKey(KeyMapping keyUse) {
        if (keyUse == null) return null;
        try {
            InputConstants.Key current = ((KeyMappingAccess) (Object) keyUse).micx$currentKey();
            if (current != null) return current;
        } catch (Throwable ignored) {
        }
        return keyUse.getDefaultKey();
    }

    /** 面板状态行：连点当前实际点在哪个键上、是否已跟随改键。 */
    public String useKeyStatus() {
        Minecraft client = Minecraft.getInstance();
        KeyMapping keyUse = client == null || client.options == null ? null : client.options.keyUse;
        if (keyUse == null) return "未取到使用键";
        boolean unbound = keyUse.isUnbound();
        InputConstants.Key current = currentUseKey(keyUse);
        InputConstants.Key vanilla = keyUse.getDefaultKey();
        String label = "未绑定";
        if (!unbound && current != null) {
            label = current.getType() == InputConstants.Type.MOUSE
                    ? UseClickRules.mouseName(current.getValue())
                    : current.getDisplayName().getString();
        }
        boolean rebound = !unbound && current != null && vanilla != null
                && current.getType() == vanilla.getType()
                && UseClickRules.rebound(false, vanilla.getValue(), current.getValue());
        return UseClickRules.status(unbound, label, rebound);
    }

    private boolean shouldFire(Minecraft client) {
        if (!enabled || !active) return false;
        if (client == null || client.player == null || client.level == null) return false;
        if (client.isPaused()) return false;
        if (client.gui.screen() != null) return false;
        if (client.options == null || client.options.keyUse == null) return false;
        if (!client.options.keyUse.isDown()) return false;
        if (client.player.isUsingItem()) return false;
        if (SkillCastModule.instance().enabled() && isSkillCasting()) return false;
        return true;
    }

    private boolean shouldSuppressVanilla(Minecraft client) {
        return client != null && shouldFire(client);
    }

    private void suppressVanilla(Minecraft client) {
        if (client == null) return;
        try {
            ((RightClickDelayAccess) (Object) client).micx$setRightClickDelay(4);
        } catch (Throwable ignored) {
        }
    }

    private boolean isSkillCasting() {
        return SkillCastModule.instance().isCasting();
    }

    private static int clampCps(int value) {
        return Math.max(1, Math.min(CPS_LIMIT, value));
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path current = FabricRuntime.configPath().resolve("right-clicker.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_RightClicker.cfg");
        Properties properties = ConfigProperties.load(current, legacy);
        binding = new InputBinding(ConfigProperties.integer(properties, "toggleKeyCode", DEFAULT_KEY, -108, GLFW.GLFW_KEY_LAST));
        active = ConfigProperties.bool(properties, "active", true);
        minCps = clampCps(ConfigProperties.integer(properties, "cpsMin", DEFAULT_MIN_CPS, 1, CPS_LIMIT));
        maxCps = clampCps(ConfigProperties.integer(properties, "cpsMax", DEFAULT_MAX_CPS, 1, CPS_LIMIT));
        if (minCps > maxCps) { int t = minCps; minCps = maxCps; maxCps = t; }
        String singleCps = ConfigProperties.string(properties, "cps", null);
        if (singleCps != null) {
            try {
                int cps = clampCps(Integer.parseInt(singleCps.trim()));
                minCps = cps;
                maxCps = cps;
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("toggleKeyCode", Integer.toString(binding.code()));
        properties.setProperty("active", Boolean.toString(active));
        properties.setProperty("cpsMin", Integer.toString(minCps));
        properties.setProperty("cpsMax", Integer.toString(maxCps));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("right-clicker.properties"), properties,
                    "MICx RightClicker configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save RightClicker configuration", exception);
        }
    }
}
