package dev.micx.micxfabric;

import java.util.Arrays;

/**
 * 防卡弹换弹键的选择（0.2.112 用户定稿的「左键 + Q 混合体」）。
 *
 * <p>Hypixel 里换弹有两种方式：左键（攻击键，持枪时服务端解释为重装弹）与 Q（丢物品，
 * 同样触发换弹）。1.8.9 旧代码三处防卡弹动作全是左键；26.2 移植后一直用 Q。
 * 现在按回合与 LR 状态混合：
 * <ul>
 *   <li>命中固定回合表（高难/压力回合，换弹窗口宝贵，用丢枪换弹最快坐实）→ <b>Q</b>；</li>
 *   <li>本回合有人释放过 LR（含队友）→ <b>Q</b>，且整回合锁定不回落；</li>
 *   <li>其余情况 → <b>左键</b>（对齐 1.8.9 原语义）。</li>
 * </ul>
 * 威胁豁免链（1.8.9 JamExemptState）用户定稿不重做。
 */
public final class JamReloadKeyRules {
    /** 换弹键。 */
    public enum ReloadKey { LEFT_CLICK, DROP_Q }

    /** 始终用 Q 的回合（升序，二分查找）。 */
    public static final int[] ALWAYS_Q_ROUNDS = {55, 59, 60, 75, 77, 80, 85, 87, 90, 95, 97, 100, 101};

    private JamReloadKeyRules() {
    }

    public static boolean isAlwaysQRound(int round) {
        return Arrays.binarySearch(ALWAYS_Q_ROUNDS, round) >= 0;
    }

    /** 决定本回合的换弹键：回合表命中或本回合已锁存「有人放 LR」→ Q，否则左键。 */
    public static ReloadKey resolve(int round, boolean lrReleasedThisRound) {
        return isAlwaysQRound(round) || lrReleasedThisRound
                ? ReloadKey.DROP_Q : ReloadKey.LEFT_CLICK;
    }
}
