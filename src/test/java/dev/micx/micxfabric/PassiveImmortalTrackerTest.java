package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 被动无敌怪判定口径（2026-09-13 用户定稿）：
 * 任意存活玩家 5 格内 ＋ 不锁敌（移动+跟随+看着玩家三者同时才算锁敌）＋ 无真实伤害
 * 5s → 可逆层（真实伤害复原）；10s → 不可逆层（永不复原）。
 * 挥剑相关受伤（受伤前 1s 内有玩家在 3 格内挥臂）不重置计时、不触发复原。
 */
class PassiveImmortalTrackerTest {
    private static final long T0 = 1_000_000L;

    /** 怪在 (0,0)，玩家在 (3,0)：distSq=9≤25。 */
    private static final double PX = 3.0;
    private static final double PZ = 0.0;

    private void passiveTick(PassiveImmortalTracker t, int id, double x, long now) {
        t.assessTick(id, x, 0.0, PX, PZ, 9.0, 180.0f, now, true);
    }

    @Test
    void notJudgedBeforeFiveSecondsThenProvisional() {
        PassiveImmortalTracker t = new PassiveImmortalTracker();
        passiveTick(t, 42, 0.0, T0);
        passiveTick(t, 42, 0.0, T0 + 4_900);
        assertFalse(t.isExcluded(42));
        assertEquals(1, passiveTickR(t, 42, T0 + 5_000));
        assertTrue(t.isExcluded(42));
        assertEquals(PassiveImmortalTracker.TIER_PROVISIONAL, t.tierOf(42));
    }

    private int passiveTickR(PassiveImmortalTracker t, int id, long now) {
        return t.assessTick(id, 0.0, 0.0, PX, PZ, 9.0, 180.0f, now, true);
    }

    @Test
    void promotedToPermanentAtTenSeconds() {
        PassiveImmortalTracker t = new PassiveImmortalTracker();
        passiveTick(t, 42, 0.0, T0);
        assertEquals(1, passiveTickR(t, 42, T0 + 6_000));
        assertEquals(2, passiveTickR(t, 42, T0 + 10_000));
        assertEquals(PassiveImmortalTracker.TIER_PERMANENT, t.tierOf(42));
    }

    @Test
    void directToPermanentWhenFirstSeenAlreadyCalm() {
        // 首见后中断观察（超出渲染距离），11 秒后再见面且期间无真实伤害 → 直接判不可逆
        PassiveImmortalTracker t = new PassiveImmortalTracker();
        passiveTick(t, 42, 0.0, T0);
        assertEquals(0, passiveTickR(t, 42, T0 + 1_000));
        assertEquals(2, passiveTickR(t, 42, T0 + 11_000));
        assertEquals(PassiveImmortalTracker.TIER_PERMANENT, t.tierOf(42));
    }

    @Test
    void realDamageResetsClock() {
        PassiveImmortalTracker t = new PassiveImmortalTracker();
        passiveTick(t, 42, 0.0, T0);
        // 4.9s 时吃真实伤害（远处无挥臂）→ 计时重置
        assertEquals(1, t.onMobHurt(42, 0.0, 0.0, 0.0, T0 + 4_900));
        assertFalse(t.isExcluded(42));
        assertEquals(0, passiveTickR(t, 42, T0 + 9_000));
        assertEquals(1, passiveTickR(t, 42, T0 + 9_901));
    }

    @Test
    void swordCorrelatedHurtIgnored() {
        PassiveImmortalTracker t = new PassiveImmortalTracker();
        passiveTick(t, 42, 0.0, T0);
        // 玩家 2 格外挥臂，0.5s 后怪受伤 → 挥剑相关，计时不动
        t.onPlayerSwing(7, 2.0, 0.0, 0.0, T0 + 4_000);
        assertEquals(0, t.onMobHurt(42, 0.0, 0.0, 0.0, T0 + 4_500));
        assertEquals(1, passiveTickR(t, 42, T0 + 5_000));
    }

    @Test
    void swingFarAwayDoesNotCorrelate() {
        PassiveImmortalTracker t = new PassiveImmortalTracker();
        passiveTick(t, 42, 0.0, T0);
        // 4 格外挥臂（>3格）→ 不相关，受伤算真实伤害
        t.onPlayerSwing(7, 4.0, 0.0, 0.0, T0 + 4_000);
        assertEquals(1, t.onMobHurt(42, 0.0, 0.0, 0.0, T0 + 4_500));
        assertFalse(t.isExcluded(42));
    }

    @Test
    void swingOutsideWindowDoesNotCorrelate() {
        PassiveImmortalTracker t = new PassiveImmortalTracker();
        passiveTick(t, 42, 0.0, T0);
        // 挥臂在受伤前 1.5s（>1s 窗口）→ 不相关
        t.onPlayerSwing(7, 2.0, 0.0, 0.0, T0 + 3_000);
        assertEquals(1, t.onMobHurt(42, 0.0, 0.0, 0.0, T0 + 4_500));
    }

    @Test
    void chasingMobIsAggroedAndNotJudged() {
        PassiveImmortalTracker t = new PassiveImmortalTracker();
        // 追踪中：每 100ms 向玩家方向移动 0.08 格、看着玩家；起点 x=-3，60 tick 后 x=1.8 不会越过玩家
        for (int i = 0; i < 60; i++) {
            int ev = t.assessTick(42, -3.0 + i * 0.08, 0.0, PX, PZ, 9.0, 10.0f, T0 + i * 100, true);
            assertEquals(0, ev, "chasing mob must not be judged at i=" + i);
        }
        assertFalse(t.isExcluded(42));
    }

    @Test
    void standingStillLookingIsNotAggro() {
        PassiveImmortalTracker t = new PassiveImmortalTracker();
        // 站位不动、一直看着玩家：不算锁敌（缺移动），判定照常进行
        for (int i = 0; i < 55; i++) {
            int ev = t.assessTick(42, 0.0, 0.0, PX, PZ, 9.0, 10.0f, T0 + i * 100, true);
            if (i == 50) assertEquals(1, ev, "5s 静止+看着 ≠ 锁敌，应判可逆");
        }
        assertTrue(t.isExcluded(42));
    }

    @Test
    void provisionalRestoredByRealDamage() {
        PassiveImmortalTracker t = new PassiveImmortalTracker();
        passiveTick(t, 42, 0.0, T0);
        passiveTickR(t, 42, T0 + 5_100);
        assertTrue(t.isExcluded(42));
        // 粘性：之后开始锁敌（追踪）也保持排除
        for (int i = 0; i < 12; i++) {
            t.assessTick(42, -1.0 + i * 0.15, 0.0, PX, PZ, 9.0, 10.0f, T0 + 6_000 + i * 100, true);
        }
        assertTrue(t.isExcluded(42));
        // 真实伤害 → 复原
        assertEquals(2, t.onMobHurt(42, 0.0, 0.0, 0.0, T0 + 7_500));
        assertFalse(t.isExcluded(42));
    }

    @Test
    void permanentNeverRestored() {
        PassiveImmortalTracker t = new PassiveImmortalTracker();
        passiveTick(t, 42, 0.0, T0);
        passiveTickR(t, 42, T0 + 10_100);
        assertEquals(PassiveImmortalTracker.TIER_PERMANENT, t.tierOf(42));
        // 真实伤害也不复原（用户定稿：10s 不可逆）
        assertEquals(1, t.onMobHurt(42, 0.0, 0.0, 0.0, T0 + 12_000));
        assertTrue(t.isExcluded(42));
        // 挥剑相关受伤同样不影响
        t.onPlayerSwing(7, 1.0, 0.0, 0.0, T0 + 13_000);
        assertEquals(0, t.onMobHurt(42, 0.0, 0.0, 0.0, T0 + 13_500));
        assertTrue(t.isExcluded(42));
    }

    @Test
    void outOfRangeNotJudged() {
        PassiveImmortalTracker t = new PassiveImmortalTracker();
        // distSq=36 > 25：永不出判定
        for (int i = 0; i < 130; i++) {
            assertEquals(0, t.assessTick(42, 0.0, 0.0, PX, PZ, 36.0, 180.0f, T0 + i * 100, true));
        }
        assertFalse(t.isExcluded(42));
    }

    @Test
    void resetClearsEverything() {
        PassiveImmortalTracker t = new PassiveImmortalTracker();
        passiveTick(t, 42, 0.0, T0);
        passiveTickR(t, 42, T0 + 5_100);
        assertTrue(t.isExcluded(42));
        t.reset();
        assertFalse(t.isExcluded(42));
        assertEquals(0, t.trackedCount());
    }

    @Test
    void lrGateClosedNeverJudges() {
        // 用户定稿 2026-09-16：只有 LR 会造成无敌怪 → 窗口外一律不判定。
        // 破窗的怪（站着不动）与靠近扔炸弹的 Clown（同样不动、长期不吃真实伤害）
        // 以前 10 秒就会被判成不可逆无敌怪，门控后 20 秒也不判。
        PassiveImmortalTracker t = new PassiveImmortalTracker();
        for (int i = 0; i <= 200; i++) {
            assertEquals(0, t.assessTick(42, 0.0, 0.0, PX, PZ, 9.0, 180.0f, T0 + i * 100L, false));
        }
        assertFalse(t.isExcluded(42));
        assertEquals(PassiveImmortalTracker.TIER_NONE, t.tierOf(42));
    }

    @Test
    void lrGateOnlyCountsCalmTimeInsideTheWindow() {
        PassiveImmortalTracker t = new PassiveImmortalTracker();
        // 窗口外站 20 秒（不攒证据）
        for (int i = 0; i <= 200; i++) {
            assertEquals(0, t.assessTick(42, 0.0, 0.0, PX, PZ, 9.0, 180.0f, T0 + i * 100L, false));
        }
        // 窗口一开，平静时长从这一刻重新攒：4.9s 不判、5.0s 判可逆层
        assertEquals(0, t.assessTick(42, 0.0, 0.0, PX, PZ, 9.0, 180.0f, T0 + 20_100L, true));
        assertEquals(0, t.assessTick(42, 0.0, 0.0, PX, PZ, 9.0, 180.0f, T0 + 24_900L, true));
        assertEquals(1, t.assessTick(42, 0.0, 0.0, PX, PZ, 9.0, 180.0f, T0 + 25_000L, true));
        assertTrue(t.isExcluded(42));
    }

    @Test
    void lrGateDoesNotReviveAnAlreadyConfirmedImmortal() {
        // 已经在窗口内判定过的不可逆层不受门控影响：窗口关了依旧排除，也不重复报事件。
        PassiveImmortalTracker t = new PassiveImmortalTracker();
        t.assessTick(42, 0.0, 0.0, PX, PZ, 9.0, 180.0f, T0, true);
        assertEquals(2, t.assessTick(42, 0.0, 0.0, PX, PZ, 9.0, 180.0f, T0 + 10_000L, true));
        assertEquals(0, t.assessTick(42, 0.0, 0.0, PX, PZ, 9.0, 180.0f, T0 + 11_000L, false));
        assertTrue(t.isExcluded(42));
        assertEquals(PassiveImmortalTracker.TIER_PERMANENT, t.tierOf(42));
    }
}
