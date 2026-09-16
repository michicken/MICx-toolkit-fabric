package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Independent 1.8.9 Aimbot target-group HUD and group-key toggle runtime.
 * It remains enabled when the actual Aimbot is off, just like the Forge module.
 */
public final class AimbotHudModule implements Module {
    private static final AimbotHudModule INSTANCE = new AimbotHudModule();
    private static final int HOTBAR_H = 22;
    private static final int LR_CY_ABOVE_HOTBAR = 80;
    private static final int LR_RING_RADIUS = 6;
    private static final int HUD_GAP_BELOW_LR = 4;
    private static final int COLOR_ON = 0xFF55FF55;
    private static final int COLOR_OFF = 0xFF666666;
    private static final int COLOR_SEP = 0xFF444444;
    private static final int COLOR_KEY = 0xFFAAAAAA;
    private static final int COLOR_EMPTY = 0xFF555555;
    private static final int COLOR_BG = 0x99000000;
    private static final int COLOR_BORDER = 0x66FFFFFF;

    private static final String[] KEY_IDS = {
            "ignoreToo", "ignoreGolem", "ignoreSlime", "prioClown",
            "prioGiant", "closest"
    };

    private boolean enabled;
    /** 游戏结束的临时隐藏截止时刻（毫秒），0 = 不在窗口内。只影响本模块，不改用户开关状态。 */
    private volatile long hideUntilMs;
    private final Set<String> down = new HashSet<>();
    private final Set<String> heldThroughBlock = new HashSet<>();

    private AimbotHudModule() {
    }

    public static AimbotHudModule instance() {
        return INSTANCE;
    }

    /**
     * 整局游戏结束 → Aimbot HUD 立刻隐藏，{@link AimbotRules#GAME_OVER_HUD_HIDE_MS} 毫秒后
     * 自动恢复；不发任何聊天提示。
     *
     * <p>用户定稿 2026-09-16，触发点是<b>整局结束</b>而不是每回合（用户纠正：「游戏结束
     * 不是回合结束」）。只动 {@code hideUntilMs}，不碰模块开关状态（{@code enabled}），
     * 所以隐藏窗口结束后回到用户原本的设置。
     */
    public static void onGameOver() {
        AimbotConfig config = AimbotModule.instance().config();
        // Aimbot 模块被关掉时它的 tick 不会跑，配置可能一次都没读过——这里补一次（load 幂等）。
        config.load();
        if (!config.hudHideOnGameOver) return;
        INSTANCE.hideUntilMs = AimbotRules.hudHideDeadline(true, System.currentTimeMillis());
        INSTANCE.down.clear();
        INSTANCE.heldThroughBlock.clear();
    }

    /** 是否正处在游戏结束的临时隐藏窗口内。 */
    public boolean hiddenByGameOver() {
        return AimbotRules.hudHideActive(System.currentTimeMillis(), hideUntilMs);
    }

    @Override
    public String id() {
        return "aimbot_hud";
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
        this.enabled = enabled;
        if (!enabled) {
            down.clear();
            heldThroughBlock.clear();
        }
        ModuleStateStore.put(id(), enabled);
    }

    @Override
    public void resetState() {
        down.clear();
        heldThroughBlock.clear();
    }

    @Override
    public void tick(Minecraft client) {
        if (!enabled || client == null || client.player == null || client.level == null) return;
        AimbotConfig config = AimbotModule.instance().config();
        // 隐藏窗口内同样不接受分组快捷键（走 blocked 通道，窗口结束时手上还按着的键不会补触发）。
        boolean blocked = hiddenByGameOver()
                || client.gui == null || client.gui.screen() != null || client.isPaused();
        int[][] keys = keyArrays(config);
        if (blocked) {
            heldThroughBlock.clear();
            for (int i = 0; i < KEY_IDS.length; i++) {
                if (KeyChord.isAllDown(keys[i], client)) heldThroughBlock.add(KEY_IDS[i]);
            }
            down.clear();
            return;
        }
        if (!heldThroughBlock.isEmpty()) {
            for (String id : heldThroughBlock) {
                int index = indexOf(id);
                if (index >= 0 && KeyChord.isAllDown(keys[index], client)) down.add(id);
            }
            heldThroughBlock.clear();
        }

        boolean changed = false;
        for (int i = 0; i < keys.length; i++) {
            int index = i;
            if (KeyChord.isEmpty(keys[i])) {
                down.remove(KEY_IDS[i]);
                continue;
            }
            boolean physical = KeyChord.isAllDown(keys[i], client);
            boolean wasDown = down.contains(KEY_IDS[i]);
            if (physical && !wasDown) {
                down.add(KEY_IDS[i]);
                toggle(config, index);
                changed = true;
            } else if (!physical) {
                down.remove(KEY_IDS[i]);
            }
        }
        if (changed) config.save();
    }

    public void drawHud(GuiGraphicsExtractor graphics) {
        if (!enabled || hiddenByGameOver()) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null
                || (client.gui != null && client.gui.screen() != null)) return;
        AimbotConfig config = AimbotModule.instance().config();
        if (!config.showHud) return;

        int[][] keys = keyArrays(config);
        String[][] labels = hudLabels();
        int[] widths = new int[KEY_IDS.length];
        int flat = 0;
        for (String[] group : labels) {
            for (String label : group) {
                String key = keyText(keys[flat]);
                widths[flat] = Math.max(client.font.width(label), client.font.width(key));
                flat++;
            }
        }
        int totalWidth = 0;
        flat = 0;
        for (int group = 0; group < labels.length; group++) {
            if (group > 0) totalWidth += 6;
            for (int column = 0; column < labels[group].length; column++) {
                totalWidth += widths[flat++];
                if (column + 1 < labels[group].length) totalWidth += 2;
            }
        }

        int pad = 3;
        int lineHeight = client.font.lineHeight;
        int rowGap = 1;
        int logicalHeight = pad * 2 + lineHeight * (config.showKeyHints ? 2 : 1)
                + (config.showKeyHints ? rowGap : 0);
        float scaleX = HudLayoutRegistry.scaleX("aimbot_hud", 1.0f);
        float scaleY = HudLayoutRegistry.scaleY("aimbot_hud", 1.0f);
        int scaledWidth = Math.round((totalWidth + pad * 2) * scaleX);
        int scaledHeight = Math.round(logicalHeight * scaleY);
        int sw = graphics.guiWidth();
        int sh = graphics.guiHeight();
        int lrCenterY = sh - HOTBAR_H - LR_CY_ABOVE_HOTBAR;
        int bottom = lrCenterY - LR_RING_RADIUS - HUD_GAP_BELOW_LR;
        int x0 = sw / 2 + config.aimbotHudDx - scaledWidth / 2;
        int top = bottom + config.aimbotHudDy - scaledHeight;

        graphics.pose().pushMatrix();
        graphics.pose().translate(x0, top);
        graphics.pose().scale(scaleX, scaleY);
        try {
            graphics.fill(0, 0, totalWidth + pad * 2, logicalHeight, COLOR_BG);
            outline(graphics, 0, 0, totalWidth + pad * 2, logicalHeight, COLOR_BORDER);
            int x = pad;
            int index = 0;
            for (int group = 0; group < labels.length; group++) {
                if (group > 0) {
                    graphics.text(client.font, Component.literal("|"), x, pad, COLOR_SEP);
                    x += 6;
                }
                for (int column = 0; column < labels[group].length; column++) {
                    boolean on = isOn(index, config);
                    graphics.text(client.font, Component.literal(labels[group][column]), x, pad,
                            on ? COLOR_ON : COLOR_OFF);
                    if (config.showKeyHints) {
                        graphics.text(client.font, Component.literal(keyText(keys[index])), x,
                                pad + lineHeight + rowGap,
                                KeyChord.isEmpty(keys[index]) ? COLOR_EMPTY : COLOR_KEY);
                    }
                    x += widths[index++];
                    if (column + 1 < labels[group].length) x += 2;
                }
            }
            drawJoystickIndicator(graphics, client, config, logicalHeight, pad);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private void drawJoystickIndicator(GuiGraphicsExtractor graphics, Minecraft client,
                                       AimbotConfig config, int height, int pad) {
        if (!config.joystick || !AimbotModule.instance().isJoystickLocked()) return;
        double yaw = AimbotModule.instance().joyYawOff();
        double pitch = AimbotModule.instance().joyPitchOff();
        if (Math.abs(yaw) < 0.02 && Math.abs(pitch) < 0.02) return;
        int cx = -pad - 6;
        int cy = height / 2;
        double scale = 6.0 / Math.max(1.0, config.joystickSwitchDeg);
        int ox = cx + (int) Math.round(Math.max(-12.0, Math.min(12.0, yaw * scale)));
        int oy = cy - (int) Math.round(Math.max(-12.0, Math.min(12.0, pitch * scale)));
        graphics.fill(cx - 6, cy, cx + 7, cy + 1, COLOR_BORDER);
        graphics.fill(cx, cy - 6, cx + 1, cy + 7, COLOR_BORDER);
        int color = Math.hypot(yaw, pitch) >= config.joystickSwitchDeg
                ? 0xFFFF5555 : 0xFFFFFFFF;
        graphics.fill(ox - 2, oy - 2, ox + 3, oy + 3, color);
    }

    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    /**
     * HUD label groups. Flattened order must stay aligned with {@link #KEY_IDS}
     * (index 0..5 = ignoreToo/ignoreGolem/ignoreSlime/prioClown/prioGiant/closest);
     * a mismatch throws AIOOBE every frame and kills the whole HUD.
     */
    static String[][] hudLabels() {
        return new String[][]{
                {"TOO", "GOL", "SLM"},
                {"CLO", "GIA"},
                {"CLS"}
        };
    }

    private static int[][] keyArrays(AimbotConfig config) {
        return new int[][]{
                config.getIgnoreTooKey(), config.getIgnoreGolemKey(), config.getIgnoreSlimeKey(),
                config.getPrioClownKey(), config.getPrioGiantKey(),
                config.getClosestKey()
        };
    }

    private static String keyText(int[] keys) {
        return KeyChord.isEmpty(keys) ? "--" : KeyChord.display(keys);
    }

    private static boolean isOn(int index, AimbotConfig config) {
        return switch (index) {
            case 0 -> config.ignoreToo;
            case 1 -> config.ignoreGolem;
            case 2 -> config.ignoreSlime;
            case 3 -> config.prioClown;
            case 4 -> config.prioGiant;
            case 5 -> config.closest;
            default -> false;
        };
    }

    private static void toggle(AimbotConfig config, int index) {
        switch (index) {
            case 0 -> config.ignoreToo = !config.ignoreToo;
            case 1 -> config.ignoreGolem = !config.ignoreGolem;
            case 2 -> config.ignoreSlime = !config.ignoreSlime;
            case 3 -> config.setPrioClown(!config.prioClown);
            case 4 -> config.setPrioGiant(!config.prioGiant);
            case 5 -> config.closest = !config.closest;
            default -> {
            }
        }
    }

    private static int indexOf(String id) {
        for (int i = 0; i < KEY_IDS.length; i++) if (KEY_IDS[i].equals(id)) return i;
        return -1;
    }
}
