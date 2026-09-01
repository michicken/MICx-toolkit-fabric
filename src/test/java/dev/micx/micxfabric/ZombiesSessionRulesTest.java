package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZombiesSessionRulesTest {
    @Test
    void transientSidebarLossDoesNotConfirmExitImmediately() {
        // 宽限 3s：Hypixel 重建侧栏/瞬时判定失败不应误判退出（误判会在重检出后清空回合计数）
        assertFalse(ZombiesSessionRules.shouldConfirmExit(true, 10_000L, 12_999L));
        assertTrue(ZombiesSessionRules.shouldConfirmExit(true, 10_000L, 13_000L));
        assertFalse(ZombiesSessionRules.shouldConfirmExit(false, 10_000L, 13_000L));
    }

    @Test
    void lowerRoundMarksAConfirmedRestartButRoundOneDoesNot() {
        assertTrue(ZombiesSessionRules.isRoundRestart(43, 1));
        assertTrue(ZombiesSessionRules.isRoundRestart(43, 42));
        assertFalse(ZombiesSessionRules.isRoundRestart(1, 1));
        assertFalse(ZombiesSessionRules.isRoundRestart(43, 0));
    }
}
