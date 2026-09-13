package dev.micx.micxfabric;

import java.util.HashMap;
import java.util.Map;

/**
 * 无敌怪（跨回合存活）追踪：某只怪在同一局内跨过 1 次回合边界仍在场即判定为无敌怪，
 * Aimbot 不再选它、锁它（首版定稿 2026-09-11；2026-09-13 用户确认口径=只需经过一回合，
 * 与本实现一致——首见第 N 回合、第 N+1 回合首次仍在场即判，无需再等一个回合）。
 *
 * <p>口径：
 * <ul>
 *   <li>阈值 = <b>跨过 1 次回合边界</b>：第 N 回合首次出现，第 N+1 回合仍在场即判无敌
 *       （{@link #IMMORTAL_ROUND_SPAN} = 1，按首见回合与当前回合之差 ≥1 判定；
 *       注意不是「跨 2 次边界」）；</li>
 *   <li>适用于<b>所有怪（含巨人）</b>；</li>
 *   <li>回合未知（{@code round <= 0}）不判定、不记录；</li>
 *   <li>回合回退（新局重开、重连）视为重新首次出现 —— 同时天然规避上一局的 id 残留。</li>
 * </ul>
 *
 * <p>为何以实体 id 追踪：客户端实体 id 在一局内唯一；跨局时服务端重新计数，
 * 「回合回退」分支会把旧记录更新为当前回合，无需额外清理。
 * 被判定无敌的 id 不做移除（判定依赖回合差，未见 id 不影响正确性），
 * 生命周期由 {@link #reset()}（会话重置）兜底。
 */
final class ImmortalMobTracker {
    /** 判无敌所需的回合差：第 N 回合首见、第 N+1 回合仍在 → 差 1。 */
    static final int IMMORTAL_ROUND_SPAN = 1;

    private final Map<Integer, Integer> firstSeenRound = new HashMap<>();

    /**
     * 每 tick 对场上每只目标怪调用一次；返回该怪是否已判定为无敌怪。
     *
     * @param entityId     实体 id
     * @param currentRound 当前回合（ZombiesTracker.round()，未知为 ≤0）
     */
    boolean isImmortal(int entityId, int currentRound) {
        if (currentRound <= 0) return false;
        Integer first = firstSeenRound.get(entityId);
        if (first == null || currentRound < first) {
            firstSeenRound.put(entityId, currentRound);
            return false;
        }
        return currentRound - first >= IMMORTAL_ROUND_SPAN;
    }

    /** 会话重置（断线/停服/新局）时清空。 */
    void reset() {
        firstSeenRound.clear();
    }

    /** 已追踪的 id 数（测试/诊断用）。 */
    int trackedCount() {
        return firstSeenRound.size();
    }
}
