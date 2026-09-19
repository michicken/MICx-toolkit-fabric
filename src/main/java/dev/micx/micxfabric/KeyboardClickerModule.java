package dev.micx.micxfabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Native KeyMapping clicker for the 23/234/24/34 modes.
 * It queues hotbar key clicks and never constructs interaction packets itself.
 * Reload recovery uses the same native hotbar and drop (Q) mappings.
 */
public final class KeyboardClickerModule implements Module {
    private static final KeyboardClickerModule INSTANCE = new KeyboardClickerModule();
    private static final int DEFAULT_TOGGLE_KEY = InputConstants.KEY_GRAVE;
    private static final int DEFAULT_MODE_KEY = GLFW.GLFW_KEY_V;
    private static final int MIN_INTERVAL = 40;
    private static final int MAX_INTERVAL = 100;
    private static final int[][] MODES = {{}, {1, 2}, {1, 2, 3}, {1, 3}, {2, 3}};
    private static final String[] MODE_NAMES = {"OFF", "23", "234", "24", "34"};
    private static final float VERY_LOW_RATIO = 0.75f;
    private static final long JAM_DETECT_MS = 150L;
    private static final long SKIP_LOG_INTERVAL_MS = 1_000L;
    /* ---- 模式 B（保护模式，Forge 同参） ---- */
    /** 前兆阈值：磨损 ≥ 88% 且耐久不变 → 记 40ms 后按混合规则换弹（左键/Q）。 */
    private static final float JAM_PRECURSOR_RATIO = 0.88f;
    /** 模式 B 触发前兆后的切走窗口（ms）：90ms 内切到其他枪。 */
    private static final long MODE_B_SWITCH_MS = 90L;
    /** 模式 B 切到后延迟多久补一次换弹（ms）。 */
    private static final long MODE_B_DROP_MS = 40L;
    /** 待执行 Q 换弹最长等待（ms），超时放弃防悬挂。 */
    private static final long MODE_B_PENDING_MAX_DELAY_MS = 500L;
    private static final int DOWN_NORMAL = 0;
    private static final int DOWN_DOWNED = 1;
    private static final int DOWN_PROTECTING = 2;

    private boolean enabled;
    private int modeIndex;
    private int pendingMode = 1;
    private int sequenceIndex;
    private long lastClick;
    private long lastNewGameRoundStartMs;
    /** 1.8.9 对齐：按 1 立即停连点（paused），按 2/3/4 恢复；避免“按一次只停、再按才切”的两步操作。 */
    private boolean paused;
    private final boolean[] hotbarPrevDown = new boolean[4];
    private int toggleKey = DEFAULT_TOGGLE_KEY;
    private int modeKey = DEFAULT_MODE_KEY;
    private int clickInterval = 50;
    private boolean rightClickTrigger;
    /** 保护模式（模式 B，实验）：不走旧检测/保护序列，切到瞬间检测前兆补换弹（键走混合规则）。 */
    private boolean jamProtectModeB;
    /* ---- 模式 B 待执行换弹状态（键由混合规则定） ---- */
    private int pendingReloadSlot = -1;
    private long pendingReloadAt;
    private long pendingReloadSetAt;
    private boolean modeBSwitch90;
    private boolean configLoaded;
    private Properties config = new Properties();

    /** 分槽触发冷却（0.2.111）：槽位 → 冷却截止毫秒，只挡那把枪自己，其他槽照常触发。 */
    private final Map<Integer, Long> slotProtectCooldownUntil = new HashMap<>();
    /** 槽位最近 8 秒内的防卡弹触发时刻（升序），第 3 次命中升级档后清零；出窗即剪。 */
    private final Map<Integer, ArrayDeque<Long>> slotProtectHistory = new HashMap<>();
    private long stuckPauseUntil;
    private JamProtectionSequence pendingProtection;
    private final Map<Integer, Long> slotVeryLowSince = new HashMap<>();
    private final Map<Integer, Integer> slotLastDamage = new HashMap<>();
    private final Map<Integer, Long> slotSkipLogTime = new HashMap<>();

    private int downJamState = DOWN_NORMAL;
    private final int[] preDownStackSize = new int[9];
    private final int[] downJamSlots = new int[8];
    private int downJamCount;
    private int downJamIndex;
    private int downJamStage;
    private long downJamStageTime;
    private boolean downJamClickerWasActive;
    private int downJamPreviousSlot;
    private long downJamCooldownUntil;

    private KeyboardClickerModule() {
    }

    public static KeyboardClickerModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "keyboard_clicker";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public void setEnabled(boolean enabled) {
        loadConfig();
        this.enabled = enabled;
        if (!enabled) resetInput();
        ModuleStateStore.put(id(), enabled);
    }

    @Override
    public InputBinding primaryBinding() {
        loadConfig();
        return new InputBinding(toggleKey);
    }

    @Override
    public InputBinding secondaryBinding() {
        loadConfig();
        return new InputBinding(modeKey);
    }

    @Override
    public void onPrimaryPressed(Minecraft client, boolean newlyEnabled) {
        if (paused) paused = false;
        if (modeIndex == 0) modeIndex = pendingMode;
        else modeIndex = 0;
        sequenceIndex = 0;
        lastClick = 0L;
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(ChatMessageStyles.notice("KeyboardClicker: " + modeName()));
        }
    }

    @Override
    public void onSecondaryPressed(Minecraft client) {
        List<Integer> modes = enabledModes();
        if (modes.isEmpty()) return;
        int current = modeIndex == 0 ? pendingMode : modeIndex;
        int index = modes.indexOf(current);
        int next = modes.get((index + 1 + modes.size()) % modes.size());
        pendingMode = next;
        if (modeIndex != 0) {
            modeIndex = next;
            sequenceIndex = 0;
            lastClick = 0L;
        }
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(ChatMessageStyles.notice("KeyboardClicker mode=" + modeName()));
        }
    }

    @Override
    public void tick(Minecraft client) {
        long now = System.currentTimeMillis();
        if (client == null || client.player == null || client.level == null) {
            resetProtectionState();
            return;
        }
        if (client.gui.screen() != null || client.isPaused()) {
            resetProtectionState();
            return;
        }

        advanceProtectionSequence(client, now);
        advanceDownJamProtection(client, now);
        resetClickerModesOnNewGame();
        if (enabled) adaptForGoldenShovel(client);
        advancePendingDrop(client, now);
        checkJamDetection(client, now);
        checkDownedJamDetection(client, now);

        pollHotbarPause(client);
        if (pendingProtection != null || downJamState == DOWN_PROTECTING) return;
        if (now < stuckPauseUntil) return;
        if (modeIndex == 0 || paused || (rightClickTrigger && !client.options.keyUse.isDown())) return;
        // 模式 B 触发前兆后的 90ms 快速切走窗口
        if (now - lastClick < (modeBSwitch90 ? MODE_B_SWITCH_MS : clickInterval)) return;

        int[] sequence = MODES[modeIndex];
        if (sequence.length == 0) return;
        for (int attempts = 0; attempts < sequence.length; attempts++) {
            int hotbarSlot = sequence[(sequenceIndex + attempts) % sequence.length];
            ItemStack item = client.player.getInventory().getItem(hotbarSlot);
            // 模式 A（现状）：有耐久且不满 → 正在换弹/卡弹，跳过；模式 B：任意轮转
            if (!jamProtectModeB && !item.isEmpty() && item.getMaxDamage() > 0
                    && item.getDamageValue() > 0) {
                emitSkipDiagnostic(hotbarSlot, item, now);
                continue;
            }
            queueHotbarSlot(hotbarSlot);
            sequenceIndex = (sequenceIndex + attempts + 1) % sequence.length;
            lastClick = now;
            // 模式 B（实验）：切到瞬间检测前兆——磨损极高且当前没在变化
            // → 记 40ms 后换弹（左键/Q 由混合规则定；90ms 切走窗口内不叠加），避免同帧按键的机器特征
            if (jamProtectModeB && !item.isEmpty() && item.getMaxDamage() > 0) {
                int damage = item.getDamageValue();
                Integer previous = slotLastDamage.get(hotbarSlot);
                if (damage >= item.getMaxDamage() * JAM_PRECURSOR_RATIO
                        && previous != null && previous == damage) {
                    pendingReloadSlot = hotbarSlot;
                    pendingReloadAt = now + MODE_B_DROP_MS;
                    pendingReloadSetAt = now;
                    modeBSwitch90 = true;
                    MicxFabric.LOGGER.debug("KeyboardClicker mode B precursor on slot {}", hotbarSlot);
                }
                slotLastDamage.put(hotbarSlot, damage);
            } else {
                modeBSwitch90 = false;
            }
            return;
        }
    }

    /** 模式 B 待执行换弹：到期、未超时、仍持目标槽 → 按混合规则出左键或 Q。 */
    private void advancePendingDrop(Minecraft client, long now) {
        if (pendingReloadSlot < 0) return;
        if (now <= pendingReloadAt + MODE_B_PENDING_MAX_DELAY_MS
                && selectedSlot(client) == pendingReloadSlot) {
            queueReload(client);
            MicxFabric.LOGGER.debug("KeyboardClicker mode B reload for slot {}", pendingReloadSlot);
        }
        pendingReloadSlot = -1;
        pendingReloadAt = 0L;
        pendingReloadSetAt = 0L;
    }

    private void checkJamDetection(Minecraft client, long now) {
        if (downJamState == DOWN_PROTECTING) return;
        // 模式 B（实验）：不走旧检测/保护序列，前兆处理在轮转循环内
        if (jamProtectModeB) return;
        if (modeIndex == 0) {
            slotVeryLowSince.clear();
            slotLastDamage.clear();
            return;
        }
        for (int sequenceSlot : MODES[modeIndex]) {
            if (sequenceSlot < 0 || sequenceSlot > 8) continue;
            ItemStack item = client.player.getInventory().getItem(sequenceSlot);
            if (item.isEmpty() || item.getMaxDamage() <= 0) {
                clearJamSlot(sequenceSlot);
                continue;
            }
            int damage = item.getDamageValue();
            int maxDamage = item.getMaxDamage();
            boolean veryLow = damage >= Math.max(1, (int) (maxDamage * VERY_LOW_RATIO));
            if (!veryLow) {
                clearJamSlot(sequenceSlot);
                continue;
            }
            Integer previousDamage = slotLastDamage.get(sequenceSlot);
            if (previousDamage == null || previousDamage != damage) {
                slotLastDamage.put(sequenceSlot, damage);
                slotVeryLowSince.put(sequenceSlot, now);
                continue;
            }
            long since = slotVeryLowSince.getOrDefault(sequenceSlot, now);
            if (now - since < JAM_DETECT_MS) continue;
            clearJamSlot(sequenceSlot);
            // 冷却分槽（0.2.111 用户定稿）：这把枪在冷却里只挡它自己；恢复序列仍全局串行一次一条。
            Long coolUntil = slotProtectCooldownUntil.get(sequenceSlot);
            if ((coolUntil != null && now < coolUntil) || pendingProtection != null) continue;
            ArrayDeque<Long> history = slotProtectHistory.computeIfAbsent(sequenceSlot, k -> new ArrayDeque<>());
            while (!history.isEmpty() && now - history.peekFirst() > JamProtectionRules.ESCALATION_WINDOW_MS) {
                history.pollFirst();
            }
            history.addLast(now);
            long cooldown = JamProtectionRules.protectCooldownMs(history.size());
            if (JamProtectionRules.isEscalated(cooldown)) history.clear();
            slotProtectCooldownUntil.put(sequenceSlot, now + cooldown);
            stuckPauseUntil = now + JamProtectionRules.STUCK_PAUSE_MS;
            pendingProtection = new JamProtectionSequence(sequenceSlot,
                    client.player.getInventory().getSelectedSlot());
            MicxFabric.LOGGER.debug("KeyboardClicker jam protection triggered for slot {}", sequenceSlot);
        }
    }

    private void advanceProtectionSequence(Minecraft client, long now) {
        JamProtectionSequence sequence = pendingProtection;
        if (sequence == null) return;
        JamProtectionSequence.Action action = sequence.advance(selectedSlot(client), now);
        switch (action.kind) {
            case SELECT -> queueHotbarSlot(action.slot);
            case RELOAD -> queueReload(client);
            case RESTORE -> queueHotbarSlot(action.slot);
            case CANCEL -> cancelProtection("manual_slot_change_or_timeout");
            case COMPLETE -> pendingProtection = null;
            case NONE -> {
            }
        }
    }

    private void checkDownedJamDetection(Minecraft client, long now) {
        if (pendingProtection != null || now < downJamCooldownUntil) return;
        int[] counts = new int[9];
        boolean[] weaponSlots = new boolean[9];
        boolean allEmpty = true;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = client.player.getInventory().getItem(slot);
            counts[slot] = item.isEmpty() ? 0 : item.getCount();
            weaponSlots[slot] = !item.isEmpty() && item.getMaxDamage() > 0;
            if (counts[slot] > 0) allEmpty = false;
        }
        if (downJamState == DOWN_NORMAL) {
            if (allEmpty) {
                downJamState = DOWN_DOWNED;
            } else {
                for (int slot = 1; slot < 9; slot++) {
                    preDownStackSize[slot] = weaponSlots[slot] ? counts[slot] : 0;
                }
            }
            return;
        }
        if (downJamState != DOWN_DOWNED || allEmpty) return;
        int[] recovered = JamProtectionRules.findRecoveredGunSlots(preDownStackSize, counts, weaponSlots);
        if (recovered.length == 0) {
            downJamState = DOWN_NORMAL;
            return;
        }
        System.arraycopy(recovered, 0, downJamSlots, 0, recovered.length);
        downJamCount = recovered.length;
        downJamIndex = 0;
        downJamStage = 0;
        downJamStageTime = now;
        downJamClickerWasActive = modeIndex > 0;
        downJamPreviousSlot = selectedSlot(client);
        downJamState = DOWN_PROTECTING;
        MicxFabric.LOGGER.debug("KeyboardClicker downed jam protection triggered for {} slots", downJamCount);
    }

    private void advanceDownJamProtection(Minecraft client, long now) {
        if (downJamState != DOWN_PROTECTING) return;
        if (downJamIndex >= downJamCount) {
            finishDownJamProtection(client, now);
            return;
        }
        int targetSlot = downJamSlots[downJamIndex];
        if (downJamStage == 0) {
            queueHotbarSlot(targetSlot);
            downJamStage = 1;
            downJamStageTime = now;
        } else if (downJamStage == 1) {
            if (selectedSlot(client) == targetSlot) {
                queueReload(client);
                downJamStage = 2;
                downJamStageTime = now;
            } else if (now - downJamStageTime >= JamProtectionRules.DOWN_SLOT_WAIT_MS) {
                downJamIndex++;
                downJamStage = 0;
            }
        } else if (now - downJamStageTime >= JamProtectionRules.DOWN_GUN_WINDOW_MS) {
            downJamIndex++;
            downJamStage = 0;
        }
    }

    private void finishDownJamProtection(Minecraft client, long now) {
        int round = ZombiesTracker.instance().round();
        if (JamProtectionRules.isSpecialRound(round)) {
            queueHotbarSlot(0);
        } else if (!downJamClickerWasActive) {
            queueHotbarSlot(downJamPreviousSlot);
        }
        downJamState = DOWN_NORMAL;
        downJamCount = 0;
        downJamIndex = 0;
        downJamStage = 0;
        downJamClickerWasActive = false;
        downJamCooldownUntil = now + JamProtectionRules.DOWN_COOLDOWN_MS;
    }

    private void resetClickerModesOnNewGame() {
        ZombiesTracker tracker = ZombiesTracker.instance();
        if (!tracker.isInAlienArcadium() || tracker.round() != 1 || tracker.roundStartMs() <= 0L
                || tracker.roundStartMs() == lastNewGameRoundStartMs) return;
        lastNewGameRoundStartMs = tracker.roundStartMs();
        loadConfig();
        // 1.8.9 对齐：新开局强制恢复 23+234，其余关；modeIndex 清 OFF 不自动开
        config.setProperty("mode23", Boolean.toString(true));
        config.setProperty("mode234", Boolean.toString(true));
        config.setProperty("mode24", Boolean.toString(false));
        config.setProperty("mode34", Boolean.toString(false));
        saveConfig();
        pendingMode = 1;
        modeIndex = 0;
        paused = false;
        sequenceIndex = 0;
        lastClick = 0L;
        java.util.Arrays.fill(hotbarPrevDown, false);
        goldAdaptDone = false;
        resetProtectionState();
    }

    /** 本局金铲子已适配过（用户定稿 2026-09-19：一局只改一次键位模式）。倒地→救起会让物品
     * 消失重现，不复位本标志，否则每次救起都会重写双键勾选并把待命模式抢成铲子对应双键；
     * 仅 {@link #resetClickerModesOnNewGame}（新开局）复位。 */
    private boolean goldAdaptDone;
    private long nextGoldAdaptAt;

    private void adaptForGoldenShovel(Minecraft client) {
        if (client == null || client.player == null || client.level == null) return;
        long now = System.currentTimeMillis();
        if (now < nextGoldAdaptAt) return;
        nextGoldAdaptAt = now + 400L;
        // 扫 1~3 槽的金铲子（hotbar 索引 1/2/3，对应 2/3/4 键）
        int shovelSlot = -1;
        for (int i = 1; i <= 3; i++) {
            ItemStack s = client.player.getInventory().getItem(i);
            if (s != null && !s.isEmpty() && isGoldenShovel(s)) { shovelSlot = i; break; }
        }
        if (shovelSlot < 0) return;
        if (goldAdaptDone) return;
        // 金铲子存在：三键仍 234；双键关掉命中金铲子的那条，换成另一条非金铲子组合
        // 例如金铲子在 2 → 双键切成 34；命中 3 → 24；命中 4 → 23
        int targetDual;
        if (shovelSlot == 1) targetDual = 4;       // 34
        else if (shovelSlot == 2) targetDual = 3;  // 24
        else targetDual = 1;                       // 23
        boolean needSave = false;
        loadConfig();
        boolean want23 = targetDual == 1;
        boolean want24 = targetDual == 3;
        boolean want34 = targetDual == 4;
        if (want23 != ConfigProperties.bool(config, "mode23", true) ||
                !ConfigProperties.bool(config, "mode234", true) ||
                want24 != ConfigProperties.bool(config, "mode24", false) ||
                want34 != ConfigProperties.bool(config, "mode34", false)) {
            config.setProperty("mode23", Boolean.toString(want23));
            config.setProperty("mode234", Boolean.toString(true));
            config.setProperty("mode24", Boolean.toString(want24));
            config.setProperty("mode34", Boolean.toString(want34));
            needSave = true;
        }
        if (needSave) saveConfig();
        // pending/running 指向也要对齐
        if (modeIndex != 0) {
            // 运行中：若当前双键恰是命中金铲子的那条，立刻切走
            if ((shovelSlot == 1 && modeIndex == 1) ||
                    (shovelSlot == 2 && modeIndex == 3) ||
                    (shovelSlot == 3 && modeIndex == 1 && targetDual != 1)) {
                // 1.8.9 语义：金铲子在 4(索引3) 且当前是 23 → 换 34
                modeIndex = targetDual;
                pendingMode = targetDual;
                sequenceIndex = 0;
                lastClick = 0L;
            }
        } else {
            pendingMode = targetDual;
        }
        goldAdaptDone = true;
    }

    private static boolean isGoldenShovel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        // 26.2 未混淆：直接比注册名最稳（避免 Items 导入跨版本漂移）
        String id = stack.getItem().toString();
        // Fallback 2：读翻译键里含 golden_shovel 也算
        String k = stack.getItem().getDescriptionId();
        return k != null && k.contains("golden_shovel") || "golden_shovel".equals(id);
    }

    private void emitSkipDiagnostic(int slot, ItemStack item, long now) {
        long last = slotSkipLogTime.getOrDefault(slot, 0L);
        if (now - last < SKIP_LOG_INTERVAL_MS) return;
        slotSkipLogTime.put(slot, now);
        MicxFabric.LOGGER.debug("KeyboardClicker skipped damaged hotbar slot {} ({}/{})",
                slot, item.getDamageValue(), item.getMaxDamage());
    }

    private void clearJamSlot(int slot) {
        slotVeryLowSince.remove(slot);
        slotLastDamage.remove(slot);
    }

    private void cancelProtection(String reason) {
        if (pendingProtection != null) {
            MicxFabric.LOGGER.debug("KeyboardClicker protection cancelled: {}", reason);
        }
        pendingProtection = null;
    }

    private void resetProtectionState() {
        pendingProtection = null;
        slotProtectCooldownUntil.clear();
        slotProtectHistory.clear();
        stuckPauseUntil = 0L;
        slotVeryLowSince.clear();
        slotLastDamage.clear();
        slotSkipLogTime.clear();
        resetDownJamState();
    }

    private void resetDownJamState() {
        downJamState = DOWN_NORMAL;
        downJamCount = 0;
        downJamIndex = 0;
        downJamStage = 0;
        downJamStageTime = 0L;
        downJamClickerWasActive = false;
        downJamPreviousSlot = 0;
        downJamCooldownUntil = 0L;
        for (int i = 0; i < preDownStackSize.length; i++) preDownStackSize[i] = 0;
    }

    /** 1.8.9 对齐：按 1 立即停；按 2/3/4 仅当 paused 时恢复，避免干扰正常手操。 */
    private void pollHotbarPause(Minecraft client) {
        if (!enabled || client == null || client.getWindow() == null) return;
        long handle = client.getWindow().handle();
        boolean[] cur = new boolean[4];
        for (int i = 0; i < 4; i++) {
            cur[i] = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_1 + i) == GLFW.GLFW_PRESS;
        }
        // 按 1：上升沿立即暂停并飞到槽位 1（同帧完成，GUI/暂停时不触发）
        if (cur[0] && !hotbarPrevDown[0] && modeIndex > 0 && client.gui.screen() == null && !client.isPaused()) {
            paused = true;
            modeIndex = 0;
            sequenceIndex = 0;
            lastClick = System.currentTimeMillis() + 1000L;
            pendingReloadSlot = -1;
            pendingReloadAt = 0L;
            pendingReloadSetAt = 0L;
            modeBSwitch90 = false;
            queueHotbarSlot(0);
        } else if (!cur[0] && hotbarPrevDown[0]) {
            // 松 1 不做事；下一段处理按 2/3/4 恢复
        }
        // 按 2/3/4：仅当 paused 时恢复待命模式，上升沿生效
        for (int i = 1; i < 4; i++) {
            if (cur[i] && !hotbarPrevDown[i] && paused) {
                paused = false;
                modeIndex = pendingMode;
                sequenceIndex = 0;
                lastClick = 0L;
                break;
            }
        }
        System.arraycopy(cur, 0, hotbarPrevDown, 0, 4);
    }

    private int selectedSlot(Minecraft client) {
        return client.player.getInventory().getSelectedSlot();
    }

    private void queueHotbarSlot(int slot) {
        if (slot < 0 || slot > 8) return;
        KeyMapping.click(InputConstants.getKey(new KeyEvent(GLFW.GLFW_KEY_1 + slot, 0, 0)));
    }

    /**
     * 本回合是否有人放过 LR（0.2.112 混合换弹的联动源）。判定＝「最后一次释放时刻 ≥ 本回合开始时刻」，
     * 无锁存状态：开背包/暂停期间不采样、回合切换那一瞬的释放都不会漏判，下一 tick 照样算对。
     * LrIndicator 关闭时没人记录释放 → 视同没人放（与无敌怪 LR 门控同口径）。
     */
    private boolean lrReleasedThisRound() {
        ZombiesTracker tracker = ZombiesTracker.instance();
        int round = tracker.round();
        long roundStart = tracker.roundStartMs();
        if (round <= 0 || roundStart <= 0L) return false;
        return LrIndicatorModule.lrLastReleaseMs() >= roundStart;
    }

    /** 混合换弹（用户定稿 2026-09-17）：固定 Q 回合表或本回合有人放 LR → Q 丢枪换弹；否则左键（攻击键）换弹。 */
    private void queueReload(Minecraft client) {
        if (JamReloadKeyRules.resolve(ZombiesTracker.instance().round(), lrReleasedThisRound())
                == JamReloadKeyRules.ReloadKey.LEFT_CLICK) {
            // 攻击键 click：与切槽/Q 同一条原生 KeyMapping 流水线；0.2.97 教训——点 keyAttack
            // 实例的当前绑定键本身（KeyMapping.key 无公开 getter，走 KeyMappingAccess），
            // 物理键绑什么都等价于「左键动作」；未绑定 click 无目标，退回 Q。
            dev.micx.micxfabric.mixin.KeyMappingAccess access =
                    (dev.micx.micxfabric.mixin.KeyMappingAccess) client.options.keyAttack;
            InputConstants.Key attackKey = access.micx$currentKey();
            if (attackKey != null && attackKey != InputConstants.UNKNOWN) {
                KeyMapping.click(attackKey);
                return;
            }
        }
        queueDropKey();
    }

    /** 模拟按下 Q 键（换弹）：与数字键同一条原生 KeyMapping 流水线。 */
    private void queueDropKey() {
        KeyMapping.click(InputConstants.getKey(new KeyEvent(GLFW.GLFW_KEY_Q, 0, 0)));
    }

    @Override
    public void resetInput() {
        modeIndex = 0;
        sequenceIndex = 0;
        lastClick = 0L;
        paused = false;
        java.util.Arrays.fill(hotbarPrevDown, false);
        // 金铲子"一局只适配一次"的标志不在这里清：模块中途关开（resetInput 会在停用时触发）
        // 不算新的一局，清了就会在下次看到铲子时重复改键位模式（用户定稿 2026-09-19）。
        nextGoldAdaptAt = 0L;
        resetProtectionState();
    }

    @Override
    public void resetState() {
        resetInput();
        goldAdaptDone = false;   // 断线/退出 = 一局结束，才允许下一局重新适配
        lastNewGameRoundStartMs = 0L;
    }

    public int modeIndex() {
        return modeIndex;
    }

    public String modeName() {
        int index = modeIndex == 0 ? pendingMode : modeIndex;
        return index >= 0 && index < MODE_NAMES.length ? MODE_NAMES[index] : "OFF";
    }

    public int toggleKey() {
        loadConfig();
        return toggleKey;
    }

    public int modeKey() {
        loadConfig();
        return modeKey;
    }

    public int clickInterval() {
        loadConfig();
        return clickInterval;
    }

    public boolean rightClickTrigger() {
        loadConfig();
        return rightClickTrigger;
    }

    public boolean isMode23() { return enabledModes().contains(1); }
    public boolean isMode234() { return enabledModes().contains(2); }
    public boolean isMode24() { return enabledModes().contains(3); }
    public boolean isMode34() { return enabledModes().contains(4); }

    public void setMode23(boolean value) { setModeEnabled(1, value); }
    public void setMode234(boolean value) { setModeEnabled(2, value); }
    public void setMode24(boolean value) { setModeEnabled(3, value); }
    public void setMode34(boolean value) { setModeEnabled(4, value); }

    private void setModeEnabled(int mode, boolean value) {
        loadConfig();
        List<Integer> modes = enabledModes();
        if (!value && modes.size() <= 1 && modes.contains(mode)) return;
        config.setProperty("mode" + MODE_NAMES[mode], Boolean.toString(value));
        if (!value && modeIndex == mode) {
            pendingMode = enabledModes().stream().findFirst().orElse(1);
            modeIndex = pendingMode;
            sequenceIndex = 0;
        }
        if (value && modeIndex == 0) pendingMode = mode;
        saveConfig();
    }

    public void setToggleKey(int value) {
        loadConfig();
        toggleKey = normalize(value);
        saveConfig();
    }

    public void setModeKey(int value) {
        loadConfig();
        modeKey = normalize(value);
        saveConfig();
    }

    public void setClickInterval(int value) {
        loadConfig();
        clickInterval = Math.max(MIN_INTERVAL, Math.min(MAX_INTERVAL, value));
        saveConfig();
    }

    public void setRightClickTrigger(boolean value) {
        loadConfig();
        rightClickTrigger = value;
        saveConfig();
    }

    public boolean isJamProtectModeB() {
        loadConfig();
        return jamProtectModeB;
    }

    public void setJamProtectModeB(boolean value) {
        loadConfig();
        jamProtectModeB = value;
        if (value) {
            cancelProtection("mode_b_enabled");
            clearJamSlotAll();
            // 模式 B 不跑旧检测，清掉分槽冷却与升级历史，切回模式 A 时别继承陈旧冷却（≤2.5s）
            slotProtectCooldownUntil.clear();
            slotProtectHistory.clear();
        }
        saveConfig();
    }

    private void clearJamSlotAll() {
        slotVeryLowSince.clear();
        slotLastDamage.clear();
    }

    private List<Integer> enabledModes() {
        loadConfig();
        List<Integer> modes = new ArrayList<>();
        for (int index = 1; index < MODES.length; index++) {
            if (ConfigProperties.bool(config, "mode" + MODE_NAMES[index], index <= 2)) modes.add(index);
        }
        return modes;
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path current = FabricRuntime.configPath().resolve("keyboard-clicker.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_KeyboardClicker.cfg");
        config = ConfigProperties.load(current, legacy);
        toggleKey = ConfigProperties.integer(config, "toggleKey", DEFAULT_TOGGLE_KEY, -108, GLFW.GLFW_KEY_LAST);
        modeKey = ConfigProperties.integer(config, "modeKey", DEFAULT_MODE_KEY, -108, GLFW.GLFW_KEY_LAST);
        clickInterval = ConfigProperties.integer(config, "clickIntervalMs", 50, MIN_INTERVAL, MAX_INTERVAL);
        rightClickTrigger = ConfigProperties.bool(config, "rightClickTrigger", false);
        jamProtectModeB = ConfigProperties.bool(config, "jamProtectModeB", false);
        pendingMode = enabledModesFromConfig().stream().findFirst().orElse(1);
    }

    private List<Integer> enabledModesFromConfig() {
        List<Integer> modes = new ArrayList<>();
        for (int index = 1; index < MODES.length; index++) {
            if (ConfigProperties.bool(config, "mode" + MODE_NAMES[index], index <= 2)) modes.add(index);
        }
        return modes;
    }

    private void saveConfig() {
        Properties properties = new Properties();
        properties.putAll(config);
        properties.setProperty("toggleKey", Integer.toString(toggleKey));
        properties.setProperty("modeKey", Integer.toString(modeKey));
        properties.setProperty("clickIntervalMs", Integer.toString(clickInterval));
        properties.setProperty("rightClickTrigger", Boolean.toString(rightClickTrigger));
        properties.setProperty("jamProtectModeB", Boolean.toString(jamProtectModeB));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("keyboard-clicker.properties"), properties,
                    "MICx KeyboardClicker configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save KeyboardClicker configuration", exception);
        }
        config = properties;
    }

    private static int normalize(int value) {
        return value == 0 ? GLFW.GLFW_KEY_UNKNOWN : Math.max(-108, Math.min(GLFW.GLFW_KEY_LAST, value));
    }
}
