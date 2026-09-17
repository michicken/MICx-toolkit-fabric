package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JamProtectionRulesTest {
    @Test
    void recoveredGunSlotsIgnoreSwordAndNonWeaponChanges() {
        int[] before = {1, 4, 2, 1, 9, 3, 0, 0, 7};
        int[] current = {1, 1, 1, 1, 1, 1, 0, 0, 1};
        boolean[] weapons = {false, true, true, false, true, true, false, false, true};
        assertArrayEquals(new int[]{1, 2, 4, 5, 8},
                JamProtectionRules.findRecoveredGunSlots(before, current, weapons));
    }

    @Test
    void allEmptyRequiresNineHotbarCounts() {
        assertTrue(JamProtectionRules.allEmpty(new int[9]));
        assertFalse(JamProtectionRules.allEmpty(new int[]{0, 0, 0, 0, 0, 0, 0, 0, 1}));
        assertFalse(JamProtectionRules.allEmpty(new int[8]));
    }

    @Test
    void specialRoundsMatchForgeTableAndHighRoundTail() {
        for (int round : new int[]{53, 54, 55, 58, 65, 69, 70, 85, 90, 99}) {
            assertTrue(JamProtectionRules.isSpecialRound(round), "round=" + round);
        }
        for (int round : new int[]{1, 52, 56, 80, 84, 86, 91}) {
            assertFalse(JamProtectionRules.isSpecialRound(round), "round=" + round);
        }
    }

    @Test
    void tieredCooldownEscalatesOnThirdTrigger() {
        assertEquals(220L, JamProtectionRules.NORMAL_PROTECT_COOLDOWN_MS);
        assertEquals(2_500L, JamProtectionRules.ESCALATED_PROTECT_COOLDOWN_MS);
        assertEquals(100L, JamProtectionRules.STUCK_PAUSE_MS, "0.2.111：触发时连点暂停 200ms 改 100ms");
        assertEquals(220L, JamProtectionRules.protectCooldownMs(1));
        assertEquals(220L, JamProtectionRules.protectCooldownMs(2));
        assertEquals(2_500L, JamProtectionRules.protectCooldownMs(3));
        assertEquals(2_500L, JamProtectionRules.protectCooldownMs(4));
        assertTrue(JamProtectionRules.isEscalated(2_500L));
        assertFalse(JamProtectionRules.isEscalated(220L));
    }

    @Test
    void windowCountAndResetReproduceModuleLoop() {
        // 复刻模块的维护逻辑：剪掉 8 秒外的记录，第 3 次命中升级并清零计数。
        java.util.ArrayDeque<Long> history = new java.util.ArrayDeque<>();
        assertEquals(220L, trigger(history, 0L));
        assertEquals(220L, trigger(history, 1_000L));
        assertEquals(2_500L, trigger(history, 2_000L), "8 秒内第 3 次 → 直接 2500");
        assertTrue(history.isEmpty(), "升级后计数清零");
        assertEquals(220L, trigger(history, 3_000L), "回到常规节奏重新数");
    }

    @Test
    void triggersSpreadWiderThanEightSecondsNeverEscalate() {
        java.util.ArrayDeque<Long> history = new java.util.ArrayDeque<>();
        assertEquals(220L, trigger(history, 0L));
        assertEquals(220L, trigger(history, 5_000L));
        // 第 3 次在 t=9000：t=0 已出 8 秒窗 → 窗口内只有 2 次，仍是常规档。
        assertEquals(220L, trigger(history, 9_000L));
        assertEquals(2, history.size());
    }

    /** 与 KeyboardClickerModule.checkJamDetection 相同的窗口剪枝 + 计数 + 升级清零。 */
    private static long trigger(java.util.ArrayDeque<Long> history, long now) {
        while (!history.isEmpty() && now - history.peekFirst() > JamProtectionRules.ESCALATION_WINDOW_MS) {
            history.pollFirst();
        }
        history.addLast(now);
        long cooldown = JamProtectionRules.protectCooldownMs(history.size());
        if (JamProtectionRules.isEscalated(cooldown)) history.clear();
        return cooldown;
    }
}
