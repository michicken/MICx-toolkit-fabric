package dev.micx.micxfabric;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 快速无敌怪判定（行为证据，粘性分层；用户定稿 2026-09-13）。
 *
 * <p>判定条件（三者同时满足的快照）：距<b>任意存活玩家</b> ≤5 格 ＋ 不锁敌 ＋ 无「真实伤害」超时：
 * <ul>
 *   <li><b>5 秒</b>无真实伤害 → {@link #TIER_PROVISIONAL} 可逆无敌怪；</li>
 *   <li><b>10 秒</b>无真实伤害 → {@link #TIER_PERMANENT} 不可逆无敌怪（永不复原）。</li>
 * </ul>
 *
 * <p>「真实伤害」= 受伤红闪动画且<b>非挥剑相关</b>。挥剑相关 = 受伤前
 * {@link #SWING_WINDOW_MS} 内有玩家在 {@link #SWING_RADIUS_SQ}（3 格）内挥过主手——
 * 铁剑左键打不死无敌怪、不能证明可杀，且正是激怒来源，故不重置计时、不触发复原。
 * 无敌怪被左键激怒后开始锁敌/攻击，但<b>粘性标记跟实体身份不跟行为</b>，
 * 锁敌后依旧排除；只有可逆层被真实伤害证明可杀才复原。
 *
 * <p>「锁敌」（用户口径：移动＋一直跟随玩家＋看着玩家，三者同时）：
 * 位移 ≥{@code MOVE_MIN} 且位移方向指向最近玩家且头部朝向玩家（角度差 ≤{@link #LOOK_DEG}）
 * → {@link #AGGRO_HOLD_MS} 内视为锁敌，期间不判定。怪物打窗户/障碍（站位不动、不看玩家）
 * 不算锁敌，由真实伤害计时兜底防误判。
 *
 * <p>与 {@link ImmortalMobTracker}（跨回合存活=100% 确定的永久无敌怪）分层互补，
 * 两层任一命中即从 Aimbot 排除。回合回退（同世界新局重开）整体清空（实体 id 会被服务端复用）。
 */
final class PassiveImmortalTracker {
    /** 5 格判定半径（平方）。 */
    static final double RANGE_SQ = 25.0;
    /** 可逆层：5 秒无真实伤害。 */
    static final long PROVISIONAL_MS = 5_000L;
    /** 不可逆层：10 秒无真实伤害。 */
    static final long PERMANENT_MS = 10_000L;
    /** 受伤前 1 秒内有玩家挥臂 → 挥剑相关。 */
    static final long SWING_WINDOW_MS = 1_000L;
    /** 挥臂玩家距怪 3 格内（平方）才算挥剑相关。 */
    static final double SWING_RADIUS_SQ = 9.0;
    /** 锁敌信号后的保持时长。 */
    static final long AGGRO_HOLD_MS = 1_500L;
    /** 头部朝向玩家的角度差阈值。 */
    static final float LOOK_DEG = 30.0f;
    /** 1 秒窗口内水平位移下限（平方，0.7 格）——低于此视为站位不动。 */
    static final double MOVE_MIN_SQ = 0.49;
    /** 位移方向与指向玩家方向夹角余弦下限（约 60°）——跟随判定。 */
    static final double FOLLOW_COS = 0.5;
    /** 移动观察窗口。 */
    static final long MOVE_WINDOW_MS = 1_000L;

    static final int TIER_NONE = 0;
    static final int TIER_PROVISIONAL = 1;
    static final int TIER_PERMANENT = 2;

    private static final class Mob {
        long firstSeenMs;
        long lastRealDamageMs;
        long aggroUntilMs;
        int tier = TIER_NONE;
        final Deque<long[]> trail = new ArrayDeque<>(); // {xBits, zBits, ms}
    }

    private static final class Swing {
        final double x, y, z;
        final long atMs;

        Swing(double x, double y, double z, long atMs) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.atMs = atMs;
        }
    }

    private final Map<Integer, Mob> mobs = new HashMap<>();
    private final Map<Integer, Swing> playerSwings = new HashMap<>();

    private static long d2b(double v) { return Double.doubleToLongBits(v); }

    /** 远程玩家主手挥臂（MixinClientPacketListener 动画包入口；本地玩家由 tick 内 swinging 自喂）。 */
    void onPlayerSwing(int playerId, double x, double y, double z, long now) {
        playerSwings.put(playerId, new Swing(x, y, z, now));
        if (playerSwings.size() > 32) {
            Iterator<Map.Entry<Integer, Swing>> it = playerSwings.entrySet().iterator();
            while (it.hasNext()) {
                if (now - it.next().getValue().atMs > 30_000L) it.remove();
            }
        }
    }

    /**
     * 怪受伤（红闪）入口。
     *
     * @return 0=挥剑相关已忽略；1=真实伤害（计时重置）；2=真实伤害且可逆层已复原
     */
    int onMobHurt(int mobId, double x, double y, double z, long now) {
        boolean sword = swordCorrelated(x, y, z, now);
        Mob m = mobs.get(mobId);
        if (m == null) {
            m = new Mob();
            m.firstSeenMs = now;
            m.lastRealDamageMs = now;
            mobs.put(mobId, m);
            return sword ? 0 : 1;
        }
        if (sword) return 0;
        m.lastRealDamageMs = now;
        if (m.tier == TIER_PROVISIONAL) {
            m.tier = TIER_NONE;
            return 2;
        }
        return 1;
    }

    private boolean swordCorrelated(double x, double y, double z, long now) {
        for (Swing s : playerSwings.values()) {
            long dt = now - s.atMs;
            if (dt < 0 || dt > SWING_WINDOW_MS) continue;
            double dx = s.x - x;
            double dy = s.y - y;
            double dz = s.z - z;
            if (dx * dx + dy * dy + dz * dz <= SWING_RADIUS_SQ) return true;
        }
        return false;
    }

    /**
     * 每 tick 对每只候选怪调用（在 Aimbot ignore 开关之前，保证追踪不断档）。
     *
     * @param distSqToNearestPlayer 怪到最近存活玩家的距离平方
     * @param headYawDeltaDeg       怪头部朝向与「指向该玩家」方位角之差（绝对值）
     * @return 0=无事件；1=新判定可逆层；2=新判定不可逆层
     */
    int assessTick(int mobId, double x, double z,
                   double nearestPlayerX, double nearestPlayerZ,
                   double distSqToNearestPlayer, float headYawDeltaDeg,
                   long now) {
        Mob m = mobs.get(mobId);
        if (m == null) {
            m = new Mob();
            m.firstSeenMs = now;
            m.lastRealDamageMs = now;
            mobs.put(mobId, m);
        }
        Deque<long[]> trail = m.trail;
        trail.addLast(new long[]{d2b(x), d2b(z), now});
        while (!trail.isEmpty() && now - trail.peekFirst()[2] > MOVE_WINDOW_MS) trail.removeFirst();

        if (isAggroed(trail, x, z, nearestPlayerX, nearestPlayerZ, headYawDeltaDeg)) {
            m.aggroUntilMs = now + AGGRO_HOLD_MS;
        }

        if (m.tier == TIER_PERMANENT) return 0;
        if (distSqToNearestPlayer > RANGE_SQ) return 0;
        if (now < m.aggroUntilMs) return 0;
        long calm = now - m.lastRealDamageMs;
        if (calm >= PERMANENT_MS) {
            m.tier = TIER_PERMANENT;
            return 2;
        }
        if (calm >= PROVISIONAL_MS && m.tier == TIER_NONE) {
            m.tier = TIER_PROVISIONAL;
            return 1;
        }
        return 0;
    }

    /** 锁敌 = 移动 ＋ 位移朝向最近玩家 ＋ 头部看着玩家（用户口径三者同时）。 */
    private boolean isAggroed(Deque<long[]> trail, double x, double z,
                              double px, double pz, float headYawDeltaDeg) {
        if (Math.abs(headYawDeltaDeg) > LOOK_DEG) return false;
        if (trail.size() < 2) return false;
        long[] oldest = trail.peekFirst();
        double dx = x - Double.longBitsToDouble(oldest[0]);
        double dz = z - Double.longBitsToDouble(oldest[1]);
        double movedSq = dx * dx + dz * dz;
        if (movedSq < MOVE_MIN_SQ) return false;
        double toX = px - x;
        double toZ = pz - z;
        double toSq = toX * toX + toZ * toZ;
        if (toSq < 1.0E-4) return true;
        double dot = dx * toX + dz * toZ;
        return dot >= FOLLOW_COS * Math.sqrt(movedSq) * Math.sqrt(toSq);
    }

    /** Aimbot 选靶排除：可逆层与不可逆层都排除。 */
    boolean isExcluded(int mobId) {
        Mob m = mobs.get(mobId);
        return m != null && m.tier != TIER_NONE;
    }

    int tierOf(int mobId) {
        Mob m = mobs.get(mobId);
        return m == null ? TIER_NONE : m.tier;
    }

    /** 回合回退（同世界新局重开）：实体 id 被服务端复用，全部清空。 */
    void reset() {
        mobs.clear();
        playerSwings.clear();
    }

    int trackedCount() {
        return mobs.size();
    }

    /** 供诊断：把每只怪的分层转成简短字符串。 */
    List<String> describe() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<Integer, Mob> e : mobs.entrySet()) {
            if (e.getValue().tier != TIER_NONE) {
                out.add("#" + e.getKey() + (e.getValue().tier == TIER_PERMANENT ? "(不可逆)" : "(可逆)"));
            }
        }
        return out;
    }
}
