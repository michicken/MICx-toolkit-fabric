package dev.micx.micxfabric;

/**
 * 史莱姆波次预告状态机（纯逻辑，可单测）。Forge 同名类 1:1 移植。
 *
 * <p>相位：墨绿（远波，即将到来）→ 亮绿（当前波正在刷）→ 刷出后若本回合还有
 * 史莱姆波则转回墨绿，否则隐藏；墨绿只预告最近的一个波次；boss 波不预告。
 */
public final class SlimeForecastState {

    /** 预告相位：隐藏 / 墨绿（未来波）/ 亮绿（当前波正在刷）。 */
    public enum Phase { HIDDEN, DARK, BRIGHT }

    /**
     * 回合 → 刷史莱姆/岩浆的波次（索引 = 回合号 - 1）。
     * 数据来源：ZombiesLogger 三局日志交叉分析 + v2.18.6 R44+ 补表（与 Forge 一致）。
     */
    private static final int[][] SLIME_WAVES = new int[101][];   // round 1..100

    static {
        SLIME_WAVES[8]  = new int[] {6};                 // R9   slime 1 只
        SLIME_WAVES[9]  = new int[] {1,2,3,4,5,6};       // R10  slime 每波 1 只
        SLIME_WAVES[14] = new int[] {1,2,3,4,5,6};       // R15
        SLIME_WAVES[15] = new int[] {1,2,3,4,5,6};       // R16
        SLIME_WAVES[17] = new int[] {1,2,3,4,5,6};       // R18
        SLIME_WAVES[22] = new int[] {1,2,3,4,5,6};       // R23
        SLIME_WAVES[25] = new int[] {1,2,3};             // R26  magma 12 点全刷
        SLIME_WAVES[28] = new int[] {1,2,3};             // R29  magma
        SLIME_WAVES[30] = new int[] {1,2,3};             // R31  magma
        SLIME_WAVES[31] = new int[] {3};                 // R32  magma 少量（单局 ⚠️）
        SLIME_WAVES[32] = new int[] {1,2,3};             // R33  magma
        SLIME_WAVES[33] = new int[] {1,2,3};             // R34  magma
        SLIME_WAVES[38] = new int[] {1,2,3};             // R39  magma+slime（单局 ⚠️）
        SLIME_WAVES[42] = new int[] {1,3,5};             // R43  magma+slime（单局 ⚠️）
        // ---- v2.18.6 补表：R44+ ----
        SLIME_WAVES[46] = new int[] {1,2,3};             // R47  过渡段
        SLIME_WAVES[51] = new int[] {1,2,3,4};           // R52  过渡段
        SLIME_WAVES[54] = new int[] {1,2,3,4,5,6};       // R55  过渡段
        SLIME_WAVES[55] = new int[] {1,2,3};             // R56  过渡段
        SLIME_WAVES[56] = new int[] {1,2,3};             // R57  过渡段
        SLIME_WAVES[60] = new int[] {2,3,4,5,6};         // R61  x1 周期
        SLIME_WAVES[64] = new int[] {3,4,5,6};           // R65  x5 周期
        SLIME_WAVES[65] = new int[] {4,5,6};             // R66  x6 周期
        SLIME_WAVES[66] = new int[] {1,2,3,4,5,6};       // R67  x7 周期
        SLIME_WAVES[70] = new int[] {2,3,4,5,6};         // R71  x1 周期
        SLIME_WAVES[74] = new int[] {3,4,5,6};           // R75  x5 周期
        SLIME_WAVES[75] = new int[] {4,5,6};             // R76  x6 周期
        SLIME_WAVES[76] = new int[] {1,2,3,4,5,6};       // R77  x7 周期
        SLIME_WAVES[80] = new int[] {2,3,4,5,6};         // R81  x1 周期
        SLIME_WAVES[84] = new int[] {3,4,5,6};           // R85  x5 周期
        SLIME_WAVES[85] = new int[] {4,5,6};             // R86  x6 周期
        SLIME_WAVES[86] = new int[] {1,2,3,4,5,6};       // R87  x7 周期
        SLIME_WAVES[90] = new int[] {2,3,4,5,6};         // R91  x1 周期
        SLIME_WAVES[94] = new int[] {3,4,5,6};           // R95  x5 周期
        SLIME_WAVES[95] = new int[] {4,5,6};             // R96  x6 周期
        SLIME_WAVES[96] = new int[] {1,2,3,4,5,6};       // R97  x7 周期
    }

    /**
     * 史莱姆/岩浆固定刷怪点（{x, y, z}），地图中部 12 点。y=71.0 为实测 slime 地面层。
     */
    private static final double[][] SLIME_POINTS = {
            { -17.5, 71.0,  30.5 },
            { -11.5, 71.0,   5.5 },
            { -11.5, 71.0,  19.5 },
            {  -9.5, 71.0,   2.5 },
            {  -7.5, 71.0,  25.5 },
            {   8.5, 71.0,   2.5 },
            {  10.5, 71.0,   6.5 },
            {  10.5, 71.0,  25.5 },
            {  13.5, 71.0,  16.5 },
            {  16.5, 71.0,  29.5 },
            {  18.5, 71.0,   4.5 },
            {  19.5, 71.0,  22.5 },
    };

    private int round = -1;
    private int wave = 0;
    /** 本回合最近已刷出史莱姆的波次（0 = 尚未刷出）。 */
    private int lastSpawnedWave = 0;

    /** 更新当前回合/波次（wave 0 = 回合开始第一波刷出前）；回合变化时重置刷出标记。 */
    public void setRound(int round, int wave) {
        if (round != this.round) {
            lastSpawnedWave = 0;
        }
        this.round = round;
        this.wave = Math.max(0, wave);
    }

    /**
     * 本波第一只史莱姆/岩浆实体加入世界（幂等；分裂产生的新实体同样触发，无副作用）。
     *
     * @param currentWave 实体加入时的当前波次（调用方新鲜计算，避免缓存滞后一帧）
     */
    public void onSlimeSpawned(int currentWave) {
        if (currentWave > 0 && currentWave > lastSpawnedWave) {
            lastSpawnedWave = currentWave;
        }
    }

    /** 当前预告相位。 */
    public Phase phase() {
        int[] waves = slimeWaves(round);
        if (waves == null || waves.length == 0) return Phase.HIDDEN;
        int nextForecast = -1;
        for (int s : waves) {
            if (s >= wave && s > lastSpawnedWave) {
                nextForecast = s;
                break;
            }
        }
        if (nextForecast < 0) return Phase.HIDDEN;
        return wave == nextForecast ? Phase.BRIGHT : Phase.DARK;
    }

    public void reset() {
        setRound(-1, 0);
    }

    /** 当前回合的史莱姆波次表（表外回合返回 null）。 */
    public static int[] slimeWaves(int round) {
        if (round < 1 || round > SLIME_WAVES.length) return null;
        return SLIME_WAVES[round - 1];
    }

    /** 12 个固定刷怪点（渲染用）。 */
    public static double[][] slimePoints() {
        return SLIME_POINTS;
    }
}
