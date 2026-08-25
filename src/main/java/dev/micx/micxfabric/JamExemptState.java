package dev.micx.micxfabric;

/**
 * 防卡弹特殊回合豁免状态机（纯逻辑，可单测）。Forge 同名类 1:1 移植。
 *
 * <p>特殊回合（r59/r70/r80/r90/r100/r101）为威胁感知豁免：仅当攻击范围内存在怪物
 * （KeyboardClickerModule 每 tick 扫描）时自动停用卡弹自动换弹链路，威胁消失立即恢复；
 * 非豁免回合不受影响。ENTERED/EXITED 事件只发一次，供模块按事件弹聊天提醒
 * （模块层另有 10s 节流）；非递增的回合回落（新对局/退出对局）静默复位。</p>
 */
public final class JamExemptState {

    /** 威胁感知豁免回合集合：回合命中且威胁在场才豁免，威胁消失即恢复。 */
    public static final int[] EXEMPT_ROUNDS = {59, 70, 80, 90, 100, 101};

    public enum Event {
        NONE,
        ENTERED,
        EXITED
    }

    private boolean exempt;
    private int exemptRound;
    private int lastExemptRound;

    /** 纯判定：回合命中豁免集合且威胁在场 → 应豁免。 */
    public static boolean shouldExempt(int round, boolean threatNearby) {
        return threatNearby && isExemptRound(round);
    }

    /** 输入当前回合与威胁在场状态，返回状态切换事件（无变化返回 NONE）。 */
    public Event observe(int round, boolean threatNearby) {
        boolean nowExempt = shouldExempt(round, threatNearby);
        if (nowExempt == exempt) {
            if (nowExempt && round != exemptRound) exemptRound = round;
            return Event.NONE;
        }
        exempt = nowExempt;
        if (nowExempt) {
            exemptRound = round;
            lastExemptRound = round;
            return Event.ENTERED;
        }
        int wasExemptRound = exemptRound;
        exemptRound = 0;
        // 离开豁免：同回合（威胁消失）或回合递增视为"恢复"；
        // 新对局/退出对局（round 回落）静默复位
        return round >= wasExemptRound ? Event.EXITED : Event.NONE;
    }

    public boolean isExempt() {
        return exempt;
    }

    /** 当前豁免的回合（未豁免返回 0）。 */
    public int exemptRound() {
        return exemptRound;
    }

    /** 最近一次豁免的回合（从未豁免返回 0）。 */
    public int lastExemptRound() {
        return lastExemptRound;
    }

    public void reset() {
        exempt = false;
        exemptRound = 0;
        lastExemptRound = 0;
    }

    /** 回合是否命中豁免集合（模块据此跳过非豁免回合的威胁扫描）。 */
    public static boolean isExemptRound(int round) {
        for (int r : EXEMPT_ROUNDS) {
            if (r == round) return true;
        }
        return false;
    }
}
