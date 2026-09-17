package dev.micx.micxfabric;

import java.util.Arrays;

/** Minecraft-free rules shared by the client protection state machine and tests. */
final class JamProtectionRules {
    static final long VERY_LOW_STABLE_MS = 150L;
    /**
     * 单槽常规触发冷却。时间线口径（2026-09-17 用户二次定稿）：触发瞬间先清观察窗，
     * 可触发检查点只能落在观察窗 150ms 的整数倍上 → 实际两次触发间隔 = 第一个 ≥ 本值的检查点。
     * 220ms → 实际 ≈300ms（不是 220，也不是 450+150）。恒定校准前先核对这条链。
     */
    static final long NORMAL_PROTECT_COOLDOWN_MS = 220L;
    /** 升级冷却：同一把枪 8 秒内凑满第 3 次触发时，第 3 次直接进这一档，随后计数清零回常规节奏（实际 ≈2550ms）。 */
    static final long ESCALATED_PROTECT_COOLDOWN_MS = 2_500L;
    /** 升级判定窗口：往前数这段时间内的触发记录（含本次）参与计数。 */
    static final long ESCALATION_WINDOW_MS = 8_000L;
    /** 窗口内累计触发次数达到该值即升级。 */
    static final int ESCALATION_TRIGGER_COUNT = 3;
    static final long STUCK_PAUSE_MS = 100L;
    static final long DOWN_GUN_WINDOW_MS = 100L;
    static final long DOWN_SLOT_WAIT_MS = 150L;
    static final long DOWN_COOLDOWN_MS = 1_000L;
    static final int[] SPECIAL_ROUNDS = {53, 54, 55, 58, 65, 69, 70};

    private JamProtectionRules() {
    }

    /**
     * 升级后的触发间隔怎么算：本次触发 <b>只挡这把枪自己</b>，其他槽不受牵连。
     * 第 3 次（含本次）触发直接给升级档，并清零该槽计数；否则给常规档。
     */
    static long protectCooldownMs(int triggersInWindowIncludingCurrent) {
        return triggersInWindowIncludingCurrent >= ESCALATION_TRIGGER_COUNT
                ? ESCALATED_PROTECT_COOLDOWN_MS : NORMAL_PROTECT_COOLDOWN_MS;
    }

    static boolean isEscalated(long cooldownMs) {
        return cooldownMs >= ESCALATED_PROTECT_COOLDOWN_MS;
    }

    static boolean isSpecialRound(int round) {
        if (round <= 0) return false;
        if (Arrays.binarySearch(SPECIAL_ROUNDS, round) >= 0) return true;
        if (round > 80) {
            int last = round % 10;
            return last == 0 || last == 5 || last == 9;
        }
        return false;
    }

    static int[] findRecoveredGunSlots(int[] beforeCounts, int[] currentCounts, boolean[] weaponSlots) {
        if (beforeCounts == null || currentCounts == null || weaponSlots == null) return new int[0];
        int limit = Math.min(9, Math.min(beforeCounts.length, Math.min(currentCounts.length, weaponSlots.length)));
        int[] result = new int[Math.max(0, limit - 1)];
        int count = 0;
        for (int slot = 1; slot < limit; slot++) {
            if (weaponSlots[slot] && beforeCounts[slot] > 1 && currentCounts[slot] == 1) {
                result[count++] = slot;
            }
        }
        return Arrays.copyOf(result, count);
    }

    static boolean allEmpty(int[] counts) {
        if (counts == null || counts.length < 9) return false;
        for (int i = 0; i < 9; i++) if (counts[i] > 0) return false;
        return true;
    }
}
