package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 0.2.112 混合换弹键规则：固定 Q 回合表 ∨ 本回合 LR 锁存 → Q，否则左键。 */
class JamReloadKeyRulesTest {
    @Test
    void alwaysQTableMatchesUserList() {
        for (int round : new int[]{55, 59, 60, 75, 77, 80, 85, 87, 90, 95, 97, 100, 101}) {
            assertTrue(JamReloadKeyRules.isAlwaysQRound(round), "round=" + round);
        }
        // 表外的邻近回合不误命中（57 是 BOSS 轮但不是 Q 轮；101 在表内）
        for (int round : new int[]{1, 54, 56, 57, 58, 61, 74, 76, 79, 81, 86, 88, 94, 96, 98, 99}) {
            assertFalse(JamReloadKeyRules.isAlwaysQRound(round), "round=" + round);
        }
    }

    @Test
    void resolvePriorityAndDefaults() {
        // 普通回合无人 LR → 左键（1.8.9 原语义）
        assertEquals(JamReloadKeyRules.ReloadKey.LEFT_CLICK, JamReloadKeyRules.resolve(30, false));
        // 表内回合 → 恒 Q
        assertEquals(JamReloadKeyRules.ReloadKey.DROP_Q, JamReloadKeyRules.resolve(55, false));
        assertEquals(JamReloadKeyRules.ReloadKey.DROP_Q, JamReloadKeyRules.resolve(101, false));
        // 表外回合，本回合有人放 LR → Q
        assertEquals(JamReloadKeyRules.ReloadKey.DROP_Q, JamReloadKeyRules.resolve(30, true));
        // 大厅/回合未开始也不炸（round<=0 非表内 → 左键）
        assertEquals(JamReloadKeyRules.ReloadKey.LEFT_CLICK, JamReloadKeyRules.resolve(0, false));
    }

    @Test
    void tableIsSortedForBinarySearch() {
        int[] t = JamReloadKeyRules.ALWAYS_Q_ROUNDS;
        for (int i = 1; i < t.length; i++) assertTrue(t[i] > t[i - 1], "升序要求被破坏于索引 " + i);
    }
}
