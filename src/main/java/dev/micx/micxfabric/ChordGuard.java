package dev.micx.micxfabric;

import java.util.List;

/**
 * 组合键成员键的单键让路判定（纯逻辑，可测；用户定稿 2026-09-22）。
 *
 * <p>一个键既绑了单键、又是某组合键的成员时，两条触发路径不能各判各的边沿——
 * 否则按 V+B 组合键时，按下 V 的那一 tick 单键模块就抢跑了。规则：
 * <ul>
 *   <li>非成员键：按下即触发（与历史行为完全一致）；</li>
 *   <li>成员键：按下先挂起（PENDING）——组合键按齐则作废（SUPPRESSED，松开不再触发）；
 *       组合键没按齐、松开那一刻才触发单键。</li>
 * </ul>
 */
public final class ChordGuard {

    private ChordGuard() {
    }

    /** 成员单键的挂起阶段。 */
    public enum Phase {
        /** 已按下、等待组合键是否按齐；松开时若组合键没按齐则触发。 */
        PENDING,
        /** 组合键已按齐过：这次按住期间单键作废，松开不触发。 */
        SUPPRESSED,
    }

    /** 每 tick 对单键绑定的推进指令。 */
    public enum Step {
        /** 无动作（保持现状）。 */
        NONE,
        /** 非成员键上升沿：立即触发（历史语义）。 */
        FIRE_NOW,
        /** 成员键上升沿：进入 PENDING 挂起。 */
        ARM_PENDING,
        /** 成员键松开且组合键未按齐：此刻触发单键。 */
        FIRE_ON_RELEASE,
    }

    /** 该键是否属于任一组合键的任意槽位（0/空组合不参与）。 */
    public static boolean isMember(int code, List<int[]> chords) {
        if (code == 0 || chords == null) return false;
        for (int[] chord : chords) {
            if (chord == null) continue;
            for (int k : chord) {
                if (k == 0) break;
                if (k == code) return true;
            }
        }
        return false;
    }

    /**
     * 单键边沿推进（调用方每 tick 每绑定一次，并按返回值维护状态/触发）。
     *
     * @param member  该键属于任一组合键
     * @param down    本 tick 是否按住
     * @param blocked GUI 打开/未进世界（不触发；按下仅消费边沿，与旧语义一致）
     * @param old     上一 tick 是否按住
     * @param phase   当前挂起阶段（null = 无挂起）
     */
    public static Step advanceSingle(boolean member, boolean down, boolean blocked,
                                     boolean old, Phase phase) {
        if (!down) {
            // 松开：组合键没按齐的挂起单键此刻才触发；SUPPRESSED/无挂起不动作（调用方清状态）。
            return !blocked && phase == Phase.PENDING ? Step.FIRE_ON_RELEASE : Step.NONE;
        }
        if (!old) {
            // 上升沿：GUI 内按下只消费边沿不触发（历史行为）；成员键挂起，非成员键立即触发。
            if (blocked) return Step.NONE;
            return member ? Step.ARM_PENDING : Step.FIRE_NOW;
        }
        // 持续按住：无新边沿（挂起等待中；组合键按齐由组合键路径置 SUPPRESSED）。
        return Step.NONE;
    }
}
