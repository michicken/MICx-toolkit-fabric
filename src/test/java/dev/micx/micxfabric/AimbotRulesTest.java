package dev.micx.micxfabric;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimbotRulesTest {
    private static AimbotRules.Candidate candidate(boolean threat, boolean too, boolean giant,
                                                    boolean head, int penetration, double angle) {
        return new AimbotRules.Candidate(threat, too, giant, head, penetration, angle);
    }

    @Test
    void angleDeltaWrapsAround() {
        assertEquals(0.0, AimbotRules.angleDelta(0, 0), 1.0e-9);
        assertEquals(20.0, AimbotRules.angleDelta(350, 10), 1.0e-9);
        assertEquals(-20.0, AimbotRules.angleDelta(10, 350), 1.0e-9);
        assertEquals(90.0, AimbotRules.angleDelta(270, 0), 1.0e-9);
        assertEquals(-90.0, AimbotRules.angleDelta(0, 270), 1.0e-9);
        assertEquals(179.0, AimbotRules.angleDelta(0, 179), 1.0e-9);
        assertEquals(-179.0, AimbotRules.angleDelta(0, -179), 1.0e-9);
    }

    @Test
    void headLayerIsTopFifth() {
        assertEquals(1.6, AimbotRules.headLayerBottom(2.0), 1.0e-9);
        assertTrue(AimbotRules.isHeadLayer(1.6, 0.0, 2.0));
        assertTrue(AimbotRules.isHeadLayer(1.9, 0.0, 2.0));
        assertFalse(AimbotRules.isHeadLayer(1.59, 0.0, 2.0));
    }

    @Test
    void badHeadshotDefaultAimPointSitsBelowTheCritBand() {
        double frac = AimbotRules.BAD_HEADSHOT_BODY_FRAC_DEFAULT;
        // 必须落在爆头带（下沿 0.80）之下，否则射线会被推到头顶附近、贴着预测框上沿走
        assertTrue(frac < 0.80, "BadHS 默认瞄点必须低于爆头带下沿");
        // 也不能低到腿上去，仍要留在躯干中上段
        assertTrue(frac > 0.55, "BadHS 默认瞄点不应低到腿部");
        // 相对历史胸腔系数 0.76 必须是下调（用户实测 0.76 常打空）
        assertTrue(frac < 0.76, "新默认值应低于历史胸腔系数 0.76");
        // 26.2 实测体型换算：僵尸 sized(0.6,1.95) / 骷髅 sized(0.6,1.99)
        assertEquals(1.2675, 1.95 * frac, 1.0e-9);
        assertEquals(1.2935, 1.99 * frac, 1.0e-9);
    }

    @Test
    void bodyFallbackKeepsLegacyOrderingForNormalMobs() {
        // 普通怪：分数 = -frac，即自上而下第一个可见采样胜出（移植前行为）
        assertEquals(-0.95, AimbotRules.bodyFallbackScore(0.95, false, Double.NaN), 1.0e-9);
        assertEquals(-0.10, AimbotRules.bodyFallbackScore(0.10, false, Double.NaN), 1.0e-9);
        assertTrue(AimbotRules.bodyFallbackScore(0.95, false, Double.NaN)
                < AimbotRules.bodyFallbackScore(0.50, false, Double.NaN));
    }

    @Test
    void bodyFallbackForBadHeadshotPrefersDownThenAllowsUp() {
        double preferred = 0.65;
        double down = AimbotRules.bodyFallbackScore(0.60, true, preferred);
        double up = AimbotRules.bodyFallbackScore(0.70, true, preferred);

        assertEquals(0.05, down, 1.0e-9);
        // 同样接近首选点时，下方采样优先（下优先）
        assertTrue(down < up);
        // 明显更远的采样排在后面
        assertTrue(down < AimbotRules.bodyFallbackScore(0.95, true, preferred));
        assertTrue(AimbotRules.bodyFallbackScore(0.95, true, preferred)
                < AimbotRules.bodyFallbackScore(0.10, true, preferred));

        // 只有上方可见时，往上兜底仍然可用（上可用）
        double onlyUp = AimbotRules.bodyFallbackScore(0.80, true, preferred);
        assertTrue(Double.isFinite(onlyUp));
        assertEquals(0.15, onlyUp, 1.0e-5);

        // 非法输入不参与兜底
        assertEquals(Double.POSITIVE_INFINITY,
                AimbotRules.bodyFallbackScore(Double.NaN, true, preferred));
    }

    @Test
    void giantAimFracDefaultSitsInsideTheHeadBandNearTheTop() {
        double frac = AimbotRules.GIANT_AIM_FRAC_DEFAULT;
        double height = 12.0;                       // 26.2 GIANT sized(3.6, 12.0)
        double aimY = height * frac;
        // 用户定稿口径：0.999 -> 脚上 11.988 格、距箱顶 0.012 格
        assertEquals(11.988, aimY, 1.0e-9);
        // 必须落在爆头带（下沿 0.80 = 9.60）之内，否则丢掉爆头线优先
        assertTrue(AimbotRules.isHeadLayer(aimY, 0.0, height));
        // 贴顶但余量必须为正
        double margin = height - aimY;
        assertTrue(margin > 0.0 && margin < 0.10, "距箱顶余量应很小: " + margin);
        // 且仍然严格在箱体之内
        assertTrue(aimY < height);
    }

    @Test
    void bodyFallbackForGiantPicksTheSampleNearestTheAimPoint() {
        double preferred = AimbotRules.GIANT_AIM_FRAC_DEFAULT;
        double justUnder = AimbotRules.bodyFallbackScore(preferred - 0.05, true, preferred);
        double mid = AimbotRules.bodyFallbackScore(preferred - 0.15, true, preferred);
        double low = AimbotRules.bodyFallbackScore(0.50, true, preferred);

        assertEquals(0.05, justUnder, 1.0e-9);
        // 越接近首选越优先
        assertTrue(justUnder < mid);
        assertTrue(mid < low);
        // 与首选等距时下方优先（两者距首选同为 0.005）
        assertTrue(AimbotRules.bodyFallbackScore(preferred - 0.005, true, preferred)
                < AimbotRules.bodyFallbackScore(preferred + 0.005, true, preferred));
    }

    @Test
    void instaAimFracIsBodyMiddleNotHead() {
        double frac = AimbotRules.INSTA_AIM_FRAC;
        assertEquals(0.50, frac, 1.0e-9);
        // 0.5 是腰腹，必须明确落在爆头带（下沿 0.80）之外 —— 它从来就不是「头点」
        double height = 1.95;
        assertFalse(AimbotRules.isHeadLayer(height * frac, 0.0, height));
        assertEquals(0.975, height * frac, 1.0e-9);
    }

    @Test
    void instaGroupRankDemotesBabyAndSlimeBehindNormalMobs() {
        // 普通怪（非 baby、非史莱姆）在 insta 窗口里前置
        assertEquals(AimbotRules.GROUP_PRIORITY, AimbotRules.instaGroupRank(false, false));
        // baby 与史莱姆/岩浆怪都降到「普通怪之后」档，且同档
        assertEquals(AimbotRules.GROUP_DEPRIORITIZED, AimbotRules.instaGroupRank(true, false));
        // 2026-09-19：baby 提前逻辑删除，BRUTE 扫射生效时也不再恢复最高组
        assertTrue(AimbotRules.instaGroupRank(false, false) > AimbotRules.instaGroupRank(true, true));
    }

    @Test
    void aboveHeightIsExemptOnlyOnRound21() {
        assertTrue(AimbotRules.aboveHeightExemptRound(21));
        assertFalse(AimbotRules.aboveHeightExemptRound(20));
        assertFalse(AimbotRules.aboveHeightExemptRound(22));
        // 回合未知（未进局 / 解析失败）不豁免
        assertFalse(AimbotRules.aboveHeightExemptRound(0));
        assertFalse(AimbotRules.aboveHeightExemptRound(-1));
    }

    @Test
    void instaKillKindMatchIsCaseInsensitiveAndTolerant() {
        // 解析器正则会捕获全大写形态
        assertTrue(AimbotRules.isInstaKillKind("INSTA KILL"));
        // 字幕路径产出标题大小写
        assertTrue(AimbotRules.isInstaKillKind("Insta Kill"));
        assertTrue(AimbotRules.isInstaKillKind("insta kill"));
        // 其余道具不得误命中
        assertFalse(AimbotRules.isInstaKillKind("Double Gold"));
        assertFalse(AimbotRules.isInstaKillKind("Shopping Spree"));
        assertFalse(AimbotRules.isInstaKillKind("Max Ammo"));
        assertFalse(AimbotRules.isInstaKillKind(""));
        assertFalse(AimbotRules.isInstaKillKind("   "));
        assertFalse(AimbotRules.isInstaKillKind(null));
    }

    @Test
    void lookVectorMatchesMinecraftYawPitchConvention() {
        Vec3 forward = AimbotRules.lookFromAngles(0.0f, 0.0f);
        assertEquals(0.0, forward.x, 1.0e-9);
        assertEquals(0.0, forward.y, 1.0e-9);
        assertEquals(1.0, forward.z, 1.0e-9);

        Vec3 west = AimbotRules.lookFromAngles(90.0f, 0.0f);
        assertEquals(-1.0, west.x, 1.0e-9);
        assertEquals(0.0, west.z, 1.0e-9);

        Vec3 up = AimbotRules.lookFromAngles(0.0f, -90.0f);
        assertEquals(1.0, up.y, 1.0e-9);

        Vec3 mixed = AimbotRules.lookFromAngles(37.0f, -23.0f);
        assertEquals(1.0, mixed.length(), 1.0e-9);
    }

    @Test
    void onlySingleOakSlabIsAddedToThePenetrableWhitelist() {
        assertTrue(AimbotRules.isAllowedSingleSlab("oak_slab", false));
        assertFalse(AimbotRules.isAllowedSingleSlab("oak_slab", true));
        assertFalse(AimbotRules.isAllowedSingleSlab("double_oak_slab", false));
        assertFalse(AimbotRules.isAllowedSingleSlab("spruce_slab", false));
        assertTrue(AimbotRules.isAllowedSingleSlab("stone_brick_slab", false));
        assertFalse(AimbotRules.isAllowedSingleSlab("stone_brick_slab", true));
    }

    @Test
    void oakFenceGateIronTrapdoorAndClayAreHardSolidsThatBlockShots() {
        assertTrue(AimbotRules.isHardSolidPath("oak_fence_gate"));
        assertTrue(AimbotRules.isHardSolidPath("iron_trapdoor"));
        assertTrue(AimbotRules.isHardSolidPath("clay"));
    }

    @Test
    void nonPenetrableStairsAndIronTrapdoorBlockTheWholeCell() {
        // 用户口径 2026-09-16：不可穿透方块挡整格 1×1×1 —— 楼梯是 L 形，台阶右上角是空的，
        // 按真实碰撞形状判会让射线从空角钻过去锁到实际打不到的目标（P4 那圈楼梯的根因）。
        assertEquals(AimbotRules.CELL_BLOCK, AimbotRules.cellVerdict("dark_oak_stairs", false, true));
        assertEquals(AimbotRules.CELL_BLOCK, AimbotRules.cellVerdict("spruce_stairs", false, true));
        assertEquals(AimbotRules.CELL_BLOCK, AimbotRules.cellVerdict("oak_stairs", false, true));
        // 去皮木种此前被木种前缀表漏掉，等于当成了可穿 —— 一并锁死。
        assertEquals(AimbotRules.CELL_BLOCK,
                AimbotRules.cellVerdict("stripped_dark_oak_stairs", false, true));
        assertEquals(AimbotRules.CELL_BLOCK,
                AimbotRules.cellVerdict("stripped_spruce_stairs", false, true));
        assertEquals(AimbotRules.CELL_BLOCK,
                AimbotRules.cellVerdict("sandstone_stairs", false, true));
        assertEquals(AimbotRules.CELL_BLOCK,
                AimbotRules.cellVerdict("smooth_sandstone_stairs", false, true));
        assertEquals(AimbotRules.CELL_BLOCK,
                AimbotRules.cellVerdict("nether_brick_stairs", false, true));
        assertEquals(AimbotRules.CELL_BLOCK, AimbotRules.cellVerdict("iron_trapdoor", false, true));
        assertEquals(AimbotRules.CELL_BLOCK, AimbotRules.cellVerdict("oak_fence_gate", false, true));
        // 非木质楼梯默认仍是可穿（wsStair 开着）；关掉本开关才整格挡。
        assertEquals(AimbotRules.CELL_PASS, AimbotRules.cellVerdict("stone_brick_stairs", false, true));
        assertEquals(AimbotRules.CELL_BLOCK, AimbotRules.cellVerdict("stone_brick_stairs", false, false));
        assertEquals(AimbotRules.CELL_PASS, AimbotRules.cellVerdict("quartz_stairs", false, true));
    }

    @Test
    void smallShapedBlocksStayShapeJudgedAndPenetrable() {
        // 用户口径 2026-09-16：草 / 花 / 火把 / 地毯这类小形状方块不受整格口径影响，照旧可穿；
        // 半砖只把「可穿」那一半写死，其余半砖继续按真实形状判。
        assertEquals(AimbotRules.CELL_SHAPE, AimbotRules.cellVerdict("short_grass", false, true));
        assertEquals(AimbotRules.CELL_SHAPE, AimbotRules.cellVerdict("dandelion", false, true));
        assertEquals(AimbotRules.CELL_SHAPE, AimbotRules.cellVerdict("torch", false, true));
        assertEquals(AimbotRules.CELL_SHAPE, AimbotRules.cellVerdict("red_carpet", false, true));
        assertEquals(AimbotRules.CELL_SHAPE, AimbotRules.cellVerdict("spruce_slab", false, true));
        assertEquals(AimbotRules.CELL_SHAPE, AimbotRules.cellVerdict("double_oak_slab", true, true));
        assertEquals(AimbotRules.CELL_PASS, AimbotRules.cellVerdict("oak_slab", false, true));
        assertEquals(AimbotRules.CELL_PASS, AimbotRules.cellVerdict("stone_brick_slab", false, true));
        // 白名单方块与整格实心的未列名方块各归各位
        assertEquals(AimbotRules.CELL_PASS, AimbotRules.cellVerdict("iron_bars", false, true));
        assertEquals(AimbotRules.CELL_PASS,
                AimbotRules.cellVerdict("white_stained_glass", false, true));
        assertEquals(AimbotRules.CELL_PASS,
                AimbotRules.cellVerdict("white_stained_glass_pane", false, true));
        // 注意（既有缺口，本次不动）：无前缀的 glass / glass_pane 匹配不上 *_glass 后缀，
        // 与旧实现一致地落到「按真实形状判」—— 要不要把它们也算可穿需用户拍板。
        assertEquals(AimbotRules.CELL_SHAPE, AimbotRules.cellVerdict("glass", false, true));
        assertEquals(AimbotRules.CELL_SHAPE, AimbotRules.cellVerdict("glass_pane", false, true));
        assertEquals(AimbotRules.CELL_PASS, AimbotRules.cellVerdict("dark_oak_fence", false, true));
        assertEquals(AimbotRules.CELL_PASS, AimbotRules.cellVerdict("cobblestone_wall", false, true));
        assertEquals(AimbotRules.CELL_PASS, AimbotRules.cellVerdict("oak_door", false, true));
        assertEquals(AimbotRules.CELL_PASS, AimbotRules.cellVerdict("oak_trapdoor", false, true));
        assertEquals(AimbotRules.CELL_SHAPE, AimbotRules.cellVerdict("oak_planks", false, true));
        assertEquals(AimbotRules.CELL_SHAPE, AimbotRules.cellVerdict("stone", false, true));
        assertEquals(AimbotRules.CELL_SHAPE, AimbotRules.cellVerdict("oak_log", false, true));
        assertEquals(AimbotRules.CELL_SHAPE, AimbotRules.cellVerdict(null, false, true));
        assertEquals(AimbotRules.CELL_SHAPE, AimbotRules.cellVerdict("", false, true));
    }

    @Test
    void woodStairDetectionCoversStrippedSpecies() {
        assertTrue(AimbotRules.isWoodStairPath("dark_oak_stairs"));
        assertTrue(AimbotRules.isWoodStairPath("stripped_oak_stairs"));
        assertTrue(AimbotRules.isWoodStairPath("stripped_crimson_stairs"));
        assertTrue(AimbotRules.isWoodStairPath("pale_oak_stairs"));
        assertFalse(AimbotRules.isWoodStairPath("stone_brick_stairs"));
        assertFalse(AimbotRules.isWoodStairPath("sandstone_stairs"));
        assertFalse(AimbotRules.isWoodStairPath("dark_oak_planks"));
        assertFalse(AimbotRules.isWoodStairPath("stripped_oak_log"));
        assertFalse(AimbotRules.isWoodStairPath(null));
    }

    @Test
    void laterPenetrableCellMustNotOpenEarlierBlockingOne() {
        // 用户口径 2026-09-16 前半句「有一格能穿，它后面的方块全穿」只对**排在它后面**的方块成立：
        // 判据是格子结论本身，能不能把整条射线放行由 canWallShot 按格序决定（射线级，这里锁住
        // cellVerdict 的三种结论取值，防止有人把 CELL_BLOCK 与 CELL_PASS 弄反）。
        assertEquals(0, AimbotRules.CELL_SHAPE);
        assertEquals(1, AimbotRules.CELL_PASS);
        assertEquals(2, AimbotRules.CELL_BLOCK);
        assertNotEquals(AimbotRules.CELL_PASS, AimbotRules.cellVerdict("dark_oak_stairs", false, true));
        assertEquals(AimbotRules.CELL_PASS, AimbotRules.cellVerdict("iron_bars", false, true));
    }

    @Test
    void allLeavesAreHardSolidsThatBlockShots() {
        // 全木种树叶 + azalea 系（均以 _leaves 结尾）
        assertTrue(AimbotRules.isHardSolidPath("oak_leaves"));
        assertTrue(AimbotRules.isHardSolidPath("spruce_leaves"));
        assertTrue(AimbotRules.isHardSolidPath("birch_leaves"));
        assertTrue(AimbotRules.isHardSolidPath("jungle_leaves"));
        assertTrue(AimbotRules.isHardSolidPath("acacia_leaves"));
        assertTrue(AimbotRules.isHardSolidPath("dark_oak_leaves"));
        assertTrue(AimbotRules.isHardSolidPath("mangrove_leaves"));
        assertTrue(AimbotRules.isHardSolidPath("cherry_leaves"));
        assertTrue(AimbotRules.isHardSolidPath("pale_oak_leaves"));
        assertTrue(AimbotRules.isHardSolidPath("azalea_leaves"));
        assertTrue(AimbotRules.isHardSolidPath("flowering_azalea_leaves"));
        // 非树叶方块不得被后缀误伤
        assertFalse(AimbotRules.isHardSolidPath("leaves"));
        assertFalse(AimbotRules.isHardSolidPath("oak_log"));
        assertFalse(AimbotRules.isHardSolidPath("oak_planks"));
    }

    @Test
    void otherFenceGatesAndTrapdoorsRemainPenetrable() {
        assertFalse(AimbotRules.isHardSolidPath("spruce_fence_gate"));
        assertFalse(AimbotRules.isHardSolidPath("dark_oak_fence_gate"));
        assertFalse(AimbotRules.isHardSolidPath("oak_trapdoor"));
        assertFalse(AimbotRules.isHardSolidPath("spruce_trapdoor"));
        assertFalse(AimbotRules.isHardSolidPath("iron_door"));
        assertFalse(AimbotRules.isHardSolidPath("stone_brick_slab"));
        // 黏土块(clay)挡枪，但陶瓦系(terracotta)不在名单内
        assertFalse(AimbotRules.isHardSolidPath("white_terracotta"));
        assertFalse(AimbotRules.isHardSolidPath("terracotta"));
        assertFalse(AimbotRules.isHardSolidPath(null));
        assertFalse(AimbotRules.isHardSolidPath(""));
    }

    @Test
    void horizontalContinuousFallbackStaysInsideTheBox() {
        assertEquals(0.95, AimbotRules.HORIZONTAL_SCAN_KEEP, 1.0e-9);
        assertTrue(AimbotRules.HORIZONTAL_BISECT_STEPS >= 1);
        // 支撑函数：轴向滑动 = 对应半宽；对角滑动 = 两个半宽的投影和
        assertEquals(0.3, AimbotRules.slideHalfExtent(0.3, 0.3, 0.0, 1.0), 1.0e-9);
        assertEquals(0.3, AimbotRules.slideHalfExtent(0.3, 0.3, 1.0, 0.0), 1.0e-9);
        assertEquals(0.6 * Math.sqrt(0.5),
                AimbotRules.slideHalfExtent(0.3, 0.3, Math.sqrt(0.5), Math.sqrt(0.5)), 1.0e-12);
        // 僵尸（宽/深 0.6）沿轴向滑到 sEdge：距箱面仍有 1.5% 箱宽余量，必在箱内
        double sEdge = AimbotRules.slideHalfExtent(0.3, 0.3, 0.0, 1.0)
                * AimbotRules.HORIZONTAL_SCAN_KEEP;
        assertEquals(0.285, sEdge, 1.0e-9);
        assertTrue(sEdge < 0.3);
        // 二分分辨率：sEdge / 2^steps，等效无级
        assertTrue(sEdge / Math.pow(2, AimbotRules.HORIZONTAL_BISECT_STEPS) < 0.02);
    }

    @Test
    void normalPitchUsesAnAboveHorizonReserveAndHoldsCompatibleManualPitch() {
        assertEquals(-13.0, AimbotRules.normalPitchTarget(-10.0, 3.0), 1.0e-9);
        assertEquals(-4.0, AimbotRules.normalPitchTarget(-1.0, 3.0), 1.0e-9);
        assertEquals(2.0, AimbotRules.normalPitchTarget(2.0, 3.0), 1.0e-9);
        assertEquals(-1.0, AimbotRules.normalPitchTargetWithFallback(-1.0, 3.0, false), 1.0e-9);
        assertEquals(-4.0, AimbotRules.normalPitchTargetWithFallback(-1.0, 3.0, true), 1.0e-9);
        assertEquals(2.0, AimbotRules.normalPitchTargetWithFallback(2.0, 3.0, false), 1.0e-9);
        assertTrue(AimbotRules.normalPitchCompatible(-11.0, -13.0, 2.0));
        assertFalse(AimbotRules.normalPitchCompatible(-10.0, -13.0, 2.0));
    }

    @Test
    void giantAimPointKeepsTheCritBand() {
        // 原版巨人碰撞箱 3.6 × 12.0：90% 落点(10.8) 仍落在头部层 [9.6, 12.0] 内
        assertTrue(AimbotRules.isHeadLayer(10.8, 0.0, 12.0));
        assertFalse(AimbotRules.isHeadLayer(9.0, 0.0, 12.0));
        // 落点距爆头层上下边界各 1.2 格 → 爆头带容差为正且随距离收紧
        double near = AimbotRules.critPitchToleranceDeg(10.8, 0.0, 12.0, 10.0);
        double far = AimbotRules.critPitchToleranceDeg(10.8, 0.0, 12.0, 40.0);
        assertTrue(near > far);
        assertTrue(far >= 0.2);
    }

    @Test
    void bruteSweepGatesByRoundOnly() {
        // 起始回合门控：之前不生效，到点起生效，未知回合不门控。
        // （链角/锥体那套判定已在 0.2.107 换成链式推进，见下面的链式用例。）
        assertFalse(AimbotRules.bruteSweepAllowed(52, 53));
        assertTrue(AimbotRules.bruteSweepAllowed(53, 53));
        assertTrue(AimbotRules.bruteSweepAllowed(80, 53));
        assertTrue(AimbotRules.bruteSweepAllowed(0, 53));
        assertTrue(AimbotRules.bruteSweepAllowed(-1, 53));
    }

    @Test
    void bruteTurnWindowDrainsOneTickWithoutOvershooting() {
        final double tick = 1.0 / 20.0;
        final double window = 25.0 / 1000.0;
        double delta = 45.0;

        // 半个窗口就该走完一半
        assertEquals(22.5, AimbotRules.renderStepFromTickDelta(delta, window / 2.0, window), 1.0E-9);

        // 25ms 窗口在两个 16.6ms 帧内应按余量封顶，累计恰好等于整份转向量
        double remaining = delta;
        double applied = 0.0;
        for (int i = 0; i < 2; i++) {
            double step = AimbotRules.limitToRemaining(
                    AimbotRules.renderStepFromTickDelta(delta, 16.6 / 1000.0, window), remaining);
            remaining -= step;
            applied += step;
        }
        assertEquals(delta, applied, 1.0E-9);

        // 余量耗尽后再来的帧不得继续推视角（否则短窗口会重复消耗、转过头）
        assertEquals(0.0, AimbotRules.limitToRemaining(
                AimbotRules.renderStepFromTickDelta(delta, tick, window), remaining), 0.0);

        // 反向：队列在一半时被刷新，旧方向的步长不参与
        assertEquals(0.0, AimbotRules.limitToRemaining(1.0, -1.0), 0.0);
        // 退化输入一律归零
        assertEquals(0.0, AimbotRules.limitToRemaining(Double.NaN, 1.0), 0.0);
        assertEquals(0.0, AimbotRules.limitToRemaining(1.0, Double.NaN), 0.0);
        assertEquals(0.0, AimbotRules.limitToRemaining(0.0, 1.0), 0.0);
    }

    /* ---- BRUTE 链式扫射状态机（0.2.107，用户定稿 2026-09-17）----
     * 旧口径「锚定锥体 + 升序推进到头绕回 + 锥体过期重锚到准星」已整体删除：锚点被扫射自己
     * 拖着走，越扫越偏，几乎扫遍全图（用户实测）。现在的口径是：
     * 只换到「与当前这只夹角 ≤ 链角」的邻接怪 → 当前方向没有就翻向 → 两边都没有就停住锁当前。
     * 另两条历史回归同样适用于新口径：①扫射不能退化成锁单只；②目标死后必须续链而不是卡住。
     */

    @Test
    void bruteChainHopsToTheNearestNeighborInTheSweepDirection() {
        int[] ids = {11, 22, 33};
        double[] yaw = {-20.0, -2.0, 15.0};
        // 当前在 11（-20°），方向从左到右 → 最近的右侧邻接怪是 22（差 18°）
        AimbotRules.BruteChainPick pick = AimbotRules.bruteChainDecision(ids, yaw,
                11, -20.0, AimbotRules.BRUTE_DIR_RIGHT, 45.0, 1_000L, 0L, 100);
        assertEquals(22, pick.entityId());
        assertTrue(pick.moved());
        assertEquals(-2.0, pick.yaw(), 1.0E-9);
        assertEquals(AimbotRules.BRUTE_DIR_RIGHT, pick.direction());
        // 停留计时重置为 now + dwell
        assertEquals(1_100L, pick.holdUntilMs());
    }

    @Test
    void bruteChainStartsFromThePoolHeadWhenTheCursorIsEmpty() {
        // 0.2.115 崩端回归：刚开扫射时 heldId=-1、curYaw=NaN（空游标），
        // 旧代码把 yawOf 的 null 直接拆箱 → NPE，一开扫射就崩。
        int[] ids = {11, 22, 33};
        double[] yaw = {-20.0, -2.0, 15.0};
        AimbotRules.BruteChainPick first = AimbotRules.bruteChainDecision(ids, yaw,
                -1, Double.NaN, AimbotRules.BRUTE_DIR_RIGHT, 45.0, 1_000L, 0L, 100);
        assertEquals(11, first.entityId(), "空游标从池首（调用方已排序的最优）起链");
        assertTrue(first.moved());
        assertEquals(1_100L, first.holdUntilMs());
        // 拿着这个游标继续走 → 正常按链推进到 22，链不会断
        AimbotRules.BruteChainPick hop = AimbotRules.bruteChainDecision(ids, yaw,
                first.entityId(), first.yaw(), first.direction(), 45.0,
                first.holdUntilMs() + 1L, first.holdUntilMs(), 100);
        assertEquals(22, hop.entityId());
        // dwell<=0（不限时）时同样能起链，且停留截止 = 不限
        AimbotRules.BruteChainPick forever = AimbotRules.bruteChainDecision(ids, yaw,
                -1, Double.NaN, AimbotRules.BRUTE_DIR_RIGHT, 45.0, 1_000L, 0L, 0);
        assertEquals(11, forever.entityId());
        assertEquals(AimbotRules.BRUTE_HOLD_FOREVER, forever.holdUntilMs());
    }

    @Test
    void bruteChainHoldsTheCurrentTargetUntilDwellExpires() {
        int[] ids = {11, 22, 33};
        double[] yaw = {-20.0, -2.0, 15.0};
        // 目标还活着且未到停留上限 → 保持不换（不换 = 不停顿）
        AimbotRules.BruteChainPick held = AimbotRules.bruteChainDecision(ids, yaw,
                22, -2.0, AimbotRules.BRUTE_DIR_RIGHT, 45.0, 1_050L, 1_100L, 100);
        assertEquals(22, held.entityId());
        assertFalse(held.moved());
        assertEquals(1_100L, held.holdUntilMs());
        // 到点 → 换下一只（不管有没有打死）
        AimbotRules.BruteChainPick hop = AimbotRules.bruteChainDecision(ids, yaw,
                22, -2.0, AimbotRules.BRUTE_DIR_RIGHT, 45.0, 1_100L, 1_100L, 100);
        assertEquals(33, hop.entityId());
        assertTrue(hop.moved());
        // dwell <= 0 = 停留不设上限
        assertEquals(AimbotRules.BRUTE_HOLD_FOREVER,
                AimbotRules.bruteChainDecision(ids, yaw, 22, -2.0,
                        AimbotRules.BRUTE_DIR_RIGHT, 45.0, 9_999L, 9_999L, 0).holdUntilMs());
    }

    @Test
    void bruteChainNeverCrossesAGapWiderThanTheChainAngle() {
        // 22 在 -2°，33 在 +60°：夹角 62° > 链角 45° → 不跨过去
        int[] ids = {22, 33};
        double[] yaw = {-2.0, 60.0};
        AimbotRules.BruteChainPick pick = AimbotRules.bruteChainDecision(ids, yaw,
                22, -2.0, AimbotRules.BRUTE_DIR_RIGHT, 45.0, 5_000L, 0L, 100);
        // 右边没有邻接怪、左边也没有 → 停住锁当前，不换目标
        assertEquals(22, pick.entityId());
        assertFalse(pick.moved());
        assertEquals(AimbotRules.BRUTE_DIR_RIGHT, pick.direction());
    }

    @Test
    void bruteChainFlipsDirectionWhenTheCurrentSideIsExhausted() {
        // 当前 11（-20°），方向从左到右，右边没有邻接怪 → 翻向从右到左，换成 -60°（差 40°）
        int[] ids = {44, 11};
        double[] yaw = {-60.0, -20.0};
        AimbotRules.BruteChainPick flips = AimbotRules.bruteChainDecision(ids, yaw,
                11, -20.0, AimbotRules.BRUTE_DIR_RIGHT, 45.0, 5_000L, 0L, 100);
        assertEquals(44, flips.entityId());
        assertTrue(flips.moved());
        assertEquals(AimbotRules.BRUTE_DIR_LEFT, flips.direction());

        // 两只互在链角内 → 来回 ping-pong（这就是「从左到右，然后从右到左」的最小形态）
        int[] pair = {11, 22};
        double[] pairYaw = {-20.0, 10.0};
        AimbotRules.BruteChainPick right = AimbotRules.bruteChainDecision(pair, pairYaw,
                11, -20.0, AimbotRules.BRUTE_DIR_RIGHT, 45.0, 5_000L, 0L, 100);
        assertEquals(22, right.entityId());
        assertEquals(AimbotRules.BRUTE_DIR_RIGHT, right.direction());
        AimbotRules.BruteChainPick left = AimbotRules.bruteChainDecision(pair, pairYaw,
                22, 10.0, AimbotRules.BRUTE_DIR_RIGHT, 45.0, 5_000L, 0L, 100);
        assertEquals(11, left.entityId());
        assertEquals(AimbotRules.BRUTE_DIR_LEFT, left.direction());
    }

    @Test
    void bruteChainContinuesFromTheCursorWhenTheTargetDied() {
        // 11 死了（不在池里）：从左到右方向的邻接怪（11 的 -20° 起算）就是 22 → 续链，不卡在原地
        int[] ids = {22, 33};
        double[] yaw = {-2.0, 15.0};
        AimbotRules.BruteChainPick next = AimbotRules.bruteChainDecision(ids, yaw,
                11, -20.0, AimbotRules.BRUTE_DIR_RIGHT, 45.0, 5_000L, 0L, 100);
        assertEquals(22, next.entityId());
        assertTrue(next.moved());
        // 池里一只都没有 → 空决策，调用方回落普通选择
        assertEquals(-1, AimbotRules.bruteChainDecision(new int[0], new double[0],
                11, -20.0, AimbotRules.BRUTE_DIR_RIGHT, 45.0, 5_000L, 0L, 100).entityId());
    }

    @Test
    void bruteChainNeverHopsBackToTheMovedCurrentTarget() {
        // 0.2.117 实机回归根因：curYaw 是上一 tick 的瞄点，池内当前目标的 yaw 本 tick 已经变了。
        // 旧代码只靠「夹角不为 0」排除自己 → 移动中的当前目标成了「本方向最近邻」，
        // hop 回自己（moved=true 空转、停留计时被刷新），扫射锁死到目标死亡才换下一只。
        int[] ids = {11, 22, 33};
        double[] yaw = {-5.0, -2.0, 15.0};   // 11 本 tick 从 -20° 移到 -5°（已经跑到 22 左边）
        AimbotRules.BruteChainPick pick = AimbotRules.bruteChainDecision(ids, yaw,
                11, -20.0, AimbotRules.BRUTE_DIR_RIGHT, 45.0, 5_000L, 0L, 100);
        assertEquals(22, pick.entityId(), "必须换到方向侧的另一只，不能 hop 回自己");
        assertTrue(pick.moved());
    }

    @Test
    void bruteChainRotatesTargetsAcrossATickLoopWhileEveryoneMoves() {
        // 每 tick 全池各偏 0.5°（模拟真实移动）+ 每 50ms 一次决策 + 40ms 停留：
        // 期望固定轮转 11→22→33→22→11→22（从左到右再翻向），而不是一直锁同一只。
        int[] ids = {11, 22, 33};
        double[] base = {-20.0, -2.0, 15.0};
        int held = -1;
        double curYaw = Double.NaN;
        int dir = AimbotRules.BRUTE_DIR_RIGHT;
        long holdUntil = 0L;
        long now = 1_000L;
        StringBuilder trace = new StringBuilder();
        for (int tick = 0; tick < 6; tick++) {
            double[] yaw = new double[base.length];
            for (int i = 0; i < base.length; i++) yaw[i] = base[i] + 0.5 * tick;
            AimbotRules.BruteChainPick pick = AimbotRules.bruteChainDecision(ids, yaw,
                    held, curYaw, dir, 45.0, now, holdUntil, 40);
            trace.append(pick.entityId()).append(' ');
            held = pick.entityId();
            curYaw = pick.yaw();
            dir = pick.direction();
            holdUntil = pick.holdUntilMs();
            now += 50L;
        }
        assertEquals("11 22 33 22 11 22 ", trace.toString());
    }

    @Test
    void bruteChainHandlesWrapAroundAndDegenerateInput() {
        // 跨 0°/360°：参考 350°，右侧（yaw 递增）15° 处的怪夹角是 25°
        AimbotRules.BruteChainPick wrapped = AimbotRules.bruteChainDecision(
                new int[]{7}, new double[]{15.0}, -1, 350.0,
                AimbotRules.BRUTE_DIR_RIGHT, 45.0, 0L, 0L, 100);
        assertEquals(7, wrapped.entityId());
        assertTrue(wrapped.moved());
        // 链角 <= 0：不给邻接结论 → 停住锁当前（当前不在池内则空决策）
        assertFalse(AimbotRules.bruteChainDecision(new int[]{7}, new double[]{15.0},
                -1, 350.0, AimbotRules.BRUTE_DIR_RIGHT, 0.0, 0L, 0L, 100).moved());
        // 退化输入：null / 空 / 长度不一致一律空决策，不得越界
        assertEquals(-1, AimbotRules.BRUTE_CHAIN_NONE.entityId());
        assertFalse(AimbotRules.BRUTE_CHAIN_NONE.moved());
        assertEquals(-1, AimbotRules.bruteChainDecision(null, null, 5, 0.0,
                AimbotRules.BRUTE_DIR_RIGHT, 45.0, 0L, 0L, 100).entityId());
        assertEquals(-1, AimbotRules.bruteChainDecision(new int[]{1, 2}, new double[]{1.0},
                5, 0.0, AimbotRules.BRUTE_DIR_RIGHT, 45.0, 0L, 0L, 100).entityId());
        // curYaw 非有限（激活首帧）时用 heldId 在池内回查一次
        AimbotRules.BruteChainPick lookedUp = AimbotRules.bruteChainDecision(
                new int[]{11, 22}, new double[]{-20.0, -2.0}, 11, Double.NaN,
                AimbotRules.BRUTE_DIR_RIGHT, 45.0, 0L, 0L, 100);
        assertEquals(22, lookedUp.entityId());
        assertTrue(lookedUp.moved());
    }

    @Test
    void threatMemoryAndCandidatePriorityMatchForgeRules() {
        long now = 10_000L;
        assertTrue(AimbotRules.isThreat(6.0, 6.0, 0L, now));
        assertTrue(AimbotRules.isThreat(9.0, 6.0, now + 4_000L, now));
        assertFalse(AimbotRules.isThreat(6.01, 6.0, 0L, now));
        assertFalse(AimbotRules.isThreat(9.0, 6.0, now - 1L, now));

        AimbotRules.Candidate threat = candidate(true, false, false, false, 8, 1.0);
        AimbotRules.Candidate too = candidate(false, true, false, true, 8, 0.1);
        AimbotRules.Candidate giant = candidate(false, false, true, true, 8, 0.1);
        AimbotRules.Candidate head = candidate(false, false, false, true, 3, 0.8);
        AimbotRules.Candidate body = candidate(false, false, false, false, 3, 0.1);
        AimbotRules.Candidate fewerPen = candidate(false, false, false, true, 1, 1.0);

        assertTrue(AimbotRules.compareCandidates(threat, too) < 0);
        assertTrue(AimbotRules.compareCandidates(too, giant) < 0);
        assertTrue(AimbotRules.compareCandidates(giant, head) < 0);
        assertTrue(AimbotRules.compareCandidates(head, body) < 0);
        assertTrue(AimbotRules.compareCandidates(fewerPen, head) < 0);
    }

    @Test
    void stickySwitchOnlyAcceptsStrictUpgrade() {
        AimbotRules.Candidate normal = candidate(false, false, false, false, 2, 0.5);
        AimbotRules.Candidate betterAngle = candidate(false, false, false, false, 2, 0.05);
        AimbotRules.Candidate fewerPen = candidate(false, false, false, false, 0, 0.9);
        AimbotRules.Candidate too = candidate(false, true, false, false, 0, 0.9);
        AimbotRules.Candidate threat = candidate(true, false, false, false, 0, 0.9);

        assertFalse(AimbotRules.shouldSwitch(normal, betterAngle, false));
        assertTrue(AimbotRules.shouldSwitch(normal, fewerPen, false));
        assertTrue(AimbotRules.shouldSwitch(normal, too, false));
        assertTrue(AimbotRules.shouldSwitch(normal, threat, false));

        assertTrue(AimbotRules.shouldSwitch(normal, threat, true));
        assertFalse(AimbotRules.shouldSwitch(normal, too, true));
        assertFalse(AimbotRules.shouldSwitch(threat, candidate(true, true, false, false, 0, 0.1), true));
    }

    @Test
    void groupPriorityAndClosestMarginMatchForgeRules() {
        // 新签名：groupRank(prioClown, prioGiant, baby, clown, giant)
        // 2026-09-19：baby 无 BRUTE 提前，固定降到「普通怪之后」档（选靶侧走忽略档）
        assertEquals(AimbotRules.GROUP_DEPRIORITIZED,
                AimbotRules.groupRank(false, false, true, false, false));
        assertEquals(AimbotRules.GROUP_PRIORITY,
                AimbotRules.groupRank(true, false, false, true, false));
        // 用户定稿 2026-09-16：巨人降档改挂 Clown 模式（两个模式的对称语义见
        // clownAndGiantModesAreSymmetric）。
        assertEquals(AimbotRules.GROUP_GIANT_BACKUP,
                AimbotRules.groupRank(true, false, false, false, true));
        // 末位档排序：巨人 < 头顶高处 < baby < 普通怪（数值越小越晚锁）
        assertTrue(AimbotRules.GROUP_GIANT_BACKUP < AimbotRules.GROUP_HIGH_ABOVE);
        assertTrue(AimbotRules.GROUP_HIGH_ABOVE < AimbotRules.GROUP_DEPRIORITIZED);
        // 普通怪仍是 0，baby 降级后严格低于普通怪
        assertEquals(0, AimbotRules.groupRank(false, false, false, false, false));
        assertTrue(AimbotRules.groupRank(false, false, false, false, false)
                > AimbotRules.groupRank(false, false, true, false, false));
        // 头顶高处（调用方 Math.min 合入）：即使 baby 降级也不越过它
        assertEquals(AimbotRules.GROUP_HIGH_ABOVE,
                Math.min(AimbotRules.groupRank(false, false, false, false, false),
                        AimbotRules.GROUP_HIGH_ABOVE));
        assertEquals(AimbotRules.GROUP_HIGH_ABOVE,
                Math.min(AimbotRules.GROUP_DEPRIORITIZED, AimbotRules.GROUP_HIGH_ABOVE));
        // Clown 模式的末位巨人又高悬：取更晚的档
        assertEquals(AimbotRules.GROUP_GIANT_BACKUP,
                Math.min(AimbotRules.GROUP_GIANT_BACKUP, AimbotRules.GROUP_HIGH_ABOVE));

        assertFalse(AimbotRules.closestBetter(10.0, 13.0, 3.0));
        assertTrue(AimbotRules.closestBetter(10.0, 13.1, 3.0));
        assertFalse(AimbotRules.closestBetter(12.0, 10.0, 3.0));
    }

    @Test
    void clownAndGiantModesAreSymmetric() {
        // 用户定稿 2026-09-16（实机反馈：Clown 模式锁巨人 / Giant 模式怎么都不锁巨人）。
        // 两个开关各自管一侧：Clown 模式把小丑提进首选档、把巨人压到末位档；
        // Giant 模式把巨人提进首选档。此前降档错挂在 prioGiant 上，两个症状正好互换。
        //
        // Giant 模式：巨人进首选档（group >= 0 才进首选池，才会被扫到）
        assertEquals(AimbotRules.GROUP_PRIORITY,
                AimbotRules.groupRank(false, true, false, false, true));
        assertTrue(AimbotRules.groupRank(false, true, false, false, true) >= 0);
        // Giant 模式不影响小丑，也不影响普通怪 / baby
        assertEquals(0, AimbotRules.groupRank(false, true, false, true, false));
        assertEquals(0, AimbotRules.groupRank(false, true, false, false, false));
        assertEquals(AimbotRules.GROUP_DEPRIORITIZED,
                AimbotRules.groupRank(false, true, true, false, false));
        // Giant 模式下巨人不再是「末位」（末位档会让它被 preferred 池排除，永远不锁）
        assertNotEquals(AimbotRules.GROUP_GIANT_BACKUP,
                AimbotRules.groupRank(false, true, false, false, true));

        // Clown 模式：小丑首选，同时巨人被压到末位档（< 0 = 只在首选池全不可打时才扫）
        assertEquals(AimbotRules.GROUP_PRIORITY,
                AimbotRules.groupRank(true, false, false, true, false));
        int clownGiant = AimbotRules.groupRank(true, false, false, false, true);
        assertEquals(AimbotRules.GROUP_GIANT_BACKUP, clownGiant);
        assertTrue(clownGiant < 0, "Clown 模式下巨人必须落在降级池");
        // Clown 模式不影响普通怪 / baby
        assertEquals(0, AimbotRules.groupRank(true, false, false, false, false));
        assertEquals(AimbotRules.GROUP_DEPRIORITIZED,
                AimbotRules.groupRank(true, false, true, false, false));

        // 两个都不开：巨人按普通档参与（既不提前也不降级）
        assertEquals(0, AimbotRules.groupRank(false, false, false, false, true));

        // 2026-09-19：baby 提前逻辑（GROUP_BABY_FIRST）删除——baby 在选靶侧固定走忽略档，
        // BRUTE 扫射生效时也不再提前；groupRank 对 baby 恒返回 GROUP_DEPRIORITIZED。
        assertEquals(AimbotRules.GROUP_DEPRIORITIZED,
                AimbotRules.groupRank(true, false, true, false, false));
        assertEquals(AimbotRules.GROUP_DEPRIORITIZED,
                AimbotRules.groupRank(false, true, true, false, false));
    }

    @Test
    void gameOverHidesAimbotHudForTenSeconds() {
        // 用户定稿 2026-09-16：**整局游戏结束** → Aimbot HUD 立刻隐藏，10 秒后立即恢复，不发聊天提示。
        // 触发点是整局结束（Zombies - 时间 / SURVIVED 结算行），不是每回合结束——用户纠正过。
        assertEquals(10_000L, AimbotRules.GAME_OVER_HUD_HIDE_MS);

        // 截止时刻 = 触发时刻 + 10s；不触发时没有窗口（0）
        assertEquals(15_000L, AimbotRules.hudHideDeadline(true, 5_000L));
        assertEquals(0L, AimbotRules.hudHideDeadline(false, 5_000L));

        // 窗口内隐藏，到点那一毫秒就恢复（严格小于）
        assertTrue(AimbotRules.hudHideActive(5_000L, 15_000L));
        assertTrue(AimbotRules.hudHideActive(14_999L, 15_000L));
        assertFalse(AimbotRules.hudHideActive(15_000L, 15_000L));
        assertFalse(AimbotRules.hudHideActive(15_001L, 15_000L));
        // 没有窗口时永远不隐藏
        assertFalse(AimbotRules.hudHideActive(0L, 0L));
        assertFalse(AimbotRules.hudHideActive(123_456L, 0L));
    }

    @Test
    void joystickUsesConeTieAndDecay() {
        assertFalse(AimbotRules.isJoystickSwitching(14.9, 0.0, 15.0));
        assertTrue(AimbotRules.isJoystickSwitching(9.0, 12.0, 15.0));
        assertTrue(AimbotRules.joystickHoldCurrent(true, false, true));
        assertFalse(AimbotRules.joystickHoldCurrent(true, true, true));

        assertEquals(1, AimbotRules.joystickPickIdx(new double[]{30.0, 5.0, 40.0}, 45.0));
        assertEquals(1, AimbotRules.joystickPickIdx(new double[]{46.0, 45.0}, 45.0));
        assertEquals(-1, AimbotRules.joystickPickIdx(new double[]{46.0, 50.0}, 45.0));
        assertEquals(1, AimbotRules.joystickPickIdx(new double[]{30.0, 5.0, 5.0}, 45.0));

        float[] pushed = AimbotRules.applyJoystickOffset(170.0f, 80.0f, 40.0, -20.0);
        assertEquals(-150.0f, pushed[0], 1.0e-6f);
        assertEquals(90.0f, pushed[1], 1.0e-6f);
        assertEquals(-9.0, AimbotRules.joyDecay(-10.0, 0.9), 1.0e-9);
    }

    @Test
    void flickEntersImmediatelyAndExitsAfterSustainedSlowInput() {
        int[] accumulator = {0};
        assertFalse(AimbotRules.flickStep(false, 500.0, 700.0, 200, 16, accumulator));
        assertTrue(AimbotRules.flickStep(false, 800.0, 700.0, 200, 16, accumulator));
        assertEquals(0, accumulator[0]);

        boolean active = true;
        for (int i = 0; i < 12; i++) {
            active = AimbotRules.flickStep(active, 200.0, 700.0, 200, 16, accumulator);
            assertTrue(active);
        }
        assertFalse(AimbotRules.flickStep(active, 200.0, 700.0, 200, 16, accumulator));

        accumulator[0] = 100;
        active = AimbotRules.flickStep(true, 900.0, 700.0, 200, 16, accumulator);
        assertTrue(active);
        assertEquals(0, accumulator[0]);
        assertFalse(AimbotRules.flickStep(active, 100.0, 700.0, 200, 201, accumulator));
    }

    @Test
    void humanizeStepConvergesWithBoundedVelocity() {
        for (double target : new double[]{30.0, 90.0, 150.0, -150.0}) {
            double current = 0.0;
            double velocity = 0.0;
            double maxVelocity = 0.0;
            for (int i = 0; i < 2000; i++) {
                double distance = AimbotRules.angleDelta(current, target);
                velocity = AimbotRules.humanizeStep(distance, velocity,
                        30.0, 45.0, 9.0, 5.0);
                current += velocity;
                maxVelocity = Math.max(maxVelocity, Math.abs(velocity));
                if (Math.abs(AimbotRules.angleDelta(current, target)) < 0.05
                        && Math.abs(velocity) < 0.05) break;
            }
            assertTrue(Math.abs(AimbotRules.angleDelta(current, target)) < 0.2,
                    "target=" + target + " current=" + current);
            assertTrue(maxVelocity <= 30.0 + 1.0e-9);
        }
    }

    @Test
    void humanizeHelpersMatchLegacyGeometry() {
        assertEquals(0.0, AimbotRules.saccadePeakDegPerTick(0.0), 1.0e-9);
        assertEquals(30.0, AimbotRules.saccadePeakDegPerTick(30.0), 1.0e-9);
        assertEquals(60.0, AimbotRules.saccadePeakDegPerTick(180.0), 1.0e-9);
        assertEquals(21.6, AimbotRules.overshootForSwing(180.0, 31.0), 0.2);
        assertTrue(AimbotRules.headRadiusDeg(30.0) < 1.0);
        assertTrue(AimbotRules.headRadiusDeg(0.5) > 25.0);
        assertTrue(AimbotRules.faceUpTrigger(0.5, 0.5));
        assertFalse(AimbotRules.faceUpTrigger(0.5001, 0.5));
        assertEquals(-90.0, AimbotRules.faceUpPitchTarget(2.0, 1.62, 0.3), 1.0e-9);
        assertTrue(AimbotRules.faceUpPitchTarget(1.0, 3.62, 0.4) > 0.0);
    }

    @Test
    void faceUpExitsOnlyAfterTheConfiguredReactionDelay() {
        int[] exitCount = {0};
        assertTrue(AimbotRules.faceUpStep(false, true, true, 8, exitCount));
        for (int i = 0; i < 7; i++) {
            assertTrue(AimbotRules.faceUpStep(true, true, false, 8, exitCount));
        }
        assertEquals(7, exitCount[0]);
        assertFalse(AimbotRules.faceUpStep(true, true, false, 8, exitCount));
        assertEquals(0, exitCount[0]);
        assertFalse(AimbotRules.faceUpStep(false, false, true, 8, exitCount));
    }

    @Test
    void humanizeSweepAndPursuitRulesAreBounded() {
        assertEquals(2, AimbotRules.frontWindowPickIdx(
                new double[]{45.0, -20.0, 5.0, 31.0}, 30.0));
        assertTrue(AimbotRules.shouldSweepScan(2, 3.0, 3.0));
        assertFalse(AimbotRules.shouldSweepScan(1, 10.0, 3.0));
        assertEquals(10.0, AimbotRules.sweepScanAngleDeg(-10.0, 10.0, 0.25), 1.0e-9);
        assertTrue(AimbotRules.pursuitNeeded(-80.0, 0.0, 30.0));
        assertFalse(AimbotRules.pursuitNeededWithHysteresis(false, 29.9, 0.0, 30.0, 18.0));
        assertTrue(AimbotRules.pursuitNeededWithHysteresis(false, 30.0, 0.0, 30.0, 18.0));
        assertTrue(AimbotRules.pursuitNeededWithHysteresis(true, 20.0, 0.0, 30.0, 18.0));
        assertFalse(AimbotRules.pursuitNeededWithHysteresis(true, 17.9, 0.0, 30.0, 18.0));
        assertEquals(15.0, AimbotRules.smoothRotationStep(90.0, 30.0), 1.0e-9);
        assertEquals(5.0 / 6.0, AimbotRules.smoothRotationStep(5.0, 30.0), 1.0e-9);
        assertEquals(-30.0, AimbotRules.smoothRotationStep(-180.0, 30.0), 1.0e-9);
        assertEquals(10.0, AimbotRules.renderStepFromTickDelta(30.0, 1.0 / 60.0,
                1.0 / 20.0), 1.0e-9);
        assertEquals(30.0, AimbotRules.renderStepFromTickDelta(30.0, 1.0 / 20.0,
                1.0 / 20.0), 1.0e-9);
        assertEquals(0.0, AimbotRules.renderStepFromTickDelta(30.0, -1.0,
                1.0 / 20.0), 1.0e-9);
        assertEquals(4.0, AimbotRules.pursuitStep(80.0, 0.0, new Random(1)), 1.0e-9);
        assertEquals(0.05, AimbotRules.quantizeRotation(0.047), 1.0e-9);
        assertEquals(90.0, AimbotRules.bruteRotationStep(90.0, 180.0), 1.0e-9);
        assertEquals(-180.0, AimbotRules.bruteRotationStep(-240.0, 180.0), 1.0e-9);
        assertEquals(0.0, AimbotRules.bruteRotationStep(90.0, 0.0), 1.0e-9);
    }

    @Test
    void nearHumanizeNoiseShrinksDuringBallisticCorrection() {
        assertEquals(0.0, AimbotRules.tremorSuppressionScale(5.0, 5.0), 1.0e-9);
        assertEquals(0.0, AimbotRules.tremorSuppressionScale(20.0, 5.0), 1.0e-9);
        assertEquals(0.5, AimbotRules.tremorSuppressionScale(2.5, 5.0), 1.0e-9);
        assertEquals(1.0, AimbotRules.tremorSuppressionScale(0.0, 5.0), 1.0e-9);
        assertEquals(1.0, AimbotRules.tremorSuppressionScale(50.0, 0.0), 1.0e-9);
    }

    @Test
    void nearSweepAmplitudeIsCappedForCloseTargets() {
        assertEquals(0.8, AimbotRules.nearSweepErrAmplitude(1.0), 1.0e-9);
        assertEquals(3.0, AimbotRules.nearSweepErrAmplitude(8.5), 1.0e-9);
        assertEquals(3.0, AimbotRules.nearSweepErrAmplitude(20.0), 1.0e-9);
        assertEquals(2.4, AimbotRules.nearSweepErrAmplitude(3.0), 1.0e-9);
    }

    @Test
    void smallestTurnAngleAlwaysBeatsCloserOrThreateningCandidate() {
        // 5° 的目标必须赢 25° 的目标，哪怕 25° 那只更近、或 5° 那只不是威胁。
        assertEquals(1, AimbotRules.frontWindowPickIdx(new double[]{25.0, 5.0}, 30.0));
        assertEquals(1, AimbotRules.frontWindowPickIdx(new double[]{-25.0, 12.0}, 30.0));
        assertEquals(-1, AimbotRules.frontWindowPickIdx(new double[]{40.0, -35.0}, 30.0));
        assertTrue(AimbotRules.compareSweepCandidates(5.0, 25.0) < 0);
        assertTrue(AimbotRules.compareSweepCandidates(-5.0, -25.0) < 0);
        assertTrue(AimbotRules.compareSweepCandidates(25.0, 5.0) > 0);
        assertEquals(0, AimbotRules.compareSweepCandidates(-8.0, 8.0));
    }

    @Test
    void midCellCoversOnlyTheUfoDropArea() {
        // UFO 四口 (±2, 105, 12/14) 正下方
        assertTrue(AimbotRules.isInsideMidCell(0.0, 13.0));
        assertTrue(AimbotRules.isInsideMidCell(2.0, 12.0));
        assertTrue(AimbotRules.isInsideMidCell(-2.0, 14.0));
        assertTrue(AimbotRules.isInsideMidCell(3.0, 15.0));
        assertTrue(AimbotRules.isInsideMidCell(-3.0, 11.0));
        // 边界外：窗户/传送点/花坛边缘之外都不算
        assertFalse(AimbotRules.isInsideMidCell(3.5, 13.0));
        assertFalse(AimbotRules.isInsideMidCell(0.0, 15.5));
        assertFalse(AimbotRules.isInsideMidCell(0.0, 10.5));
        assertFalse(AimbotRules.isInsideMidCell(-6.0, 32.0));  // p1 传送点
        assertFalse(AimbotRules.isInsideMidCell(18.0, 44.0));  // alt 摩天轮角
    }

    @Test
    void highAbovePlayerUsesFootHeightDifferenceOnly() {
        // 玩家脚底 y=72：高台上的怪（77.5）超过 5 格阈值 → 忽略
        assertTrue(AimbotRules.isTooHighAbove(77.5, 72.0, 5.0));
        // 斜坡/矮台阶（76.0，差 4 格）→ 照常打
        assertFalse(AimbotRules.isTooHighAbove(76.0, 72.0, 5.0));
        // 阈值边界：正好 5.0 不算超过
        assertFalse(AimbotRules.isTooHighAbove(77.0, 72.0, 5.0));
        // 比玩家低的怪（地下室）永远不触发
        assertFalse(AimbotRules.isTooHighAbove(68.0, 72.0, 5.0));
        // 玩家自己在高处（脚底 100），怪在 104 → 只差 4 格，不忽略
        assertFalse(AimbotRules.isTooHighAbove(104.0, 100.0, 5.0));
        // 非法值
        assertFalse(AimbotRules.isTooHighAbove(Double.NaN, 72.0, 5.0));
        assertFalse(AimbotRules.isTooHighAbove(80.0, Double.NaN, 5.0));
    }

    @Test
    void critPitchToleranceStaysInsideTheHeadBand() {
        double footY = 72.0;
        double height = 1.95;                       // 普通僵尸
        double bandBottom = footY + AimbotRules.headLayerBottom(height);   // 73.56
        double bandTop = footY + height;                                   // 73.95
        double center = (bandBottom + bandTop) / 2.0;                      // 73.755

        // 爆头带中心：容差为正，且明显小于配置默认 2°（会收紧冻结判据）
        double tol10 = AimbotRules.critPitchToleranceDeg(center, footY, height, 10.0);
        assertTrue(tol10 > 0.0 && tol10 < 2.0, "10 格容差应在 (0,2) 内，实际 " + tol10);

        // 距离越远角度越小
        double tol5 = AimbotRules.critPitchToleranceDeg(center, footY, height, 5.0);
        double tol20 = AimbotRules.critPitchToleranceDeg(center, footY, height, 20.0);
        assertTrue(tol5 > tol10 && tol10 > tol20, "容差应随距离单调变紧");

        // 容差换算回线性余量后不得超出爆头层
        double linear = Math.tan(Math.toRadians(tol10)) * 10.0;
        assertTrue(linear <= (bandTop - bandBottom) / 2.0 + 1.0E-9,
                "容差不得越出爆头层，实际 " + linear);

        // 瞄准点已降级到身体（低于 80% 线）或高于头顶 -> 无暴击带可守
        assertEquals(0.0, AimbotRules.critPitchToleranceDeg(bandBottom - 0.1, footY, height, 10.0));
        assertEquals(0.0, AimbotRules.critPitchToleranceDeg(bandTop + 0.1, footY, height, 10.0));

        // 非法输入
        assertEquals(0.0, AimbotRules.critPitchToleranceDeg(Double.NaN, footY, height, 10.0));
        assertEquals(0.0, AimbotRules.critPitchToleranceDeg(center, footY, 0.0, 10.0));

        // 极远距离仍有下限，避免永不收敛
        assertTrue(AimbotRules.critPitchToleranceDeg(center, footY, height, 1000.0)
                >= AimbotRules.CRIT_TOL_FLOOR_DEG);
    }

    @Test
    void highSpeedFallIgnoresOnlyAirborneMidDrops() {
        double groundY = 76.0;
        double fallSpeed = 1.8;
        double maxHorizontal = 0.5;
        // UFO 投放：y=95、垂直 -3.2、几乎无水平位移 → 命中
        assertTrue(AimbotRules.isHighSpeedFall(95.0, -3.2, 0.05,
                groundY, fallSpeed, maxHorizontal));
        // 刚离飞船、速度还不够快 → 不命中（等它加速到阈值再忽略）
        assertFalse(AimbotRules.isHighSpeedFall(104.0, -0.4, 0.0,
                groundY, fallSpeed, maxHorizontal));
        // 已落地（y<=76）→ 必须恢复为普通目标
        assertFalse(AimbotRules.isHighSpeedFall(72.0, -3.2, 0.0,
                groundY, fallSpeed, maxHorizontal));
        // 被打飞：垂直很快但水平位移明显 → 不能被忽略
        assertFalse(AimbotRules.isHighSpeedFall(80.0, -3.0, 1.4,
                groundY, fallSpeed, maxHorizontal));
        // 窗户下落：贴地、速度小 → 不能被忽略
        assertFalse(AimbotRules.isHighSpeedFall(73.5, -0.6, 0.1,
                groundY, fallSpeed, maxHorizontal));
        // 非法值
        assertFalse(AimbotRules.isHighSpeedFall(Double.NaN, -3.0, 0.0,
                groundY, fallSpeed, maxHorizontal));
        assertFalse(AimbotRules.isHighSpeedFall(90.0, Double.NaN, 0.0,
                groundY, fallSpeed, maxHorizontal));
    }

    private static void assertCells(int[] actual, int[][] expected) {
        assertEquals(expected.length * 3, actual.length,
                "cell 数应为 " + expected.length + "，实际 " + (actual.length / 3));
        for (int i = 0; i < expected.length; i++) {
            assertArrayEquals(expected[i],
                    new int[]{actual[i * 3], actual[i * 3 + 1], actual[i * 3 + 2]},
                    "第 " + i + " 个 cell 不匹配");
        }
    }

    @Test
    void rayCellsSameCellStartAndEnd() {
        assertCells(AimbotRules.rayCells(0.5, 0.5, 0.5, 0.7, 0.9, 0.3),
                new int[][]{{0, 0, 0}});
    }

    @Test
    void rayCellsWalksPositiveXAxisInOrder() {
        assertCells(AimbotRules.rayCells(0.5, 0.5, 0.5, 3.5, 0.5, 0.5),
                new int[][]{{0, 0, 0}, {1, 0, 0}, {2, 0, 0}, {3, 0, 0}});
    }

    @Test
    void rayCellsWalksNegativeXAxisInOrder() {
        assertCells(AimbotRules.rayCells(3.5, 0.5, 0.5, 0.5, 0.5, 0.5),
                new int[][]{{3, 0, 0}, {2, 0, 0}, {1, 0, 0}, {0, 0, 0}});
    }

    @Test
    void rayCellsEndsAtBoundaryOnTheFarCell() {
        // 终点 3.0 恰在格 3 的左边界上（MC 格子左闭右开）→ 终点格为 3，必须包含
        assertCells(AimbotRules.rayCells(0.5, 0.5, 0.5, 3.0, 0.5, 0.5),
                new int[][]{{0, 0, 0}, {1, 0, 0}, {2, 0, 0}, {3, 0, 0}});
    }

    @Test
    void rayCellsCoversBothSidesOnPerfectDiagonal() {
        // 45° 对角精确平局：侧格与对角格全部记录（格角两侧都算被整格接触，宁多勿漏）
        assertCells(AimbotRules.rayCells(0.5, 0.5, 0.5, 2.5, 2.5, 0.5),
                new int[][]{{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {2, 1, 0}, {2, 2, 0}});
    }

    @Test
    void rayCellsWalksVerticalStack() {
        assertCells(AimbotRules.rayCells(0.5, 1.5, 0.5, 0.5, 4.5, 0.5),
                new int[][]{{0, 1, 0}, {0, 2, 0}, {0, 3, 0}, {0, 4, 0}});
    }

    @Test
    void rayCellsHandlesNegativeCoordinates() {
        assertCells(AimbotRules.rayCells(-0.5, 0.5, 0.5, 1.5, 0.5, 0.5),
                new int[][]{{-1, 0, 0}, {0, 0, 0}, {1, 0, 0}});
    }

    // ---- 瞄准点竖扫（OceanClient 口径：0.05 格步长、自上而下首个可见即停）----

    @Test
    void downwardScanWalksEveryFiveCentimetres() {
        // 僵尸幽灵框：脚 64.00，首选 0.9 → 65.80；0.05 步长一格格往下
        int count = AimbotRules.scanLayerCount(65.80, 64.0, AimbotRules.AIM_SCAN_STEP, 999);
        assertEquals(37, count);                       // (65.80-64.00)/0.05 + 1
        assertEquals(65.80, AimbotRules.scanLayerY(65.80, 64.0, 0, AimbotRules.AIM_SCAN_STEP), 1.0e-9);
        assertEquals(65.75, AimbotRules.scanLayerY(65.80, 64.0, 1, AimbotRules.AIM_SCAN_STEP), 1.0e-9);
        assertEquals(64.00, AimbotRules.scanLayerY(65.80, 64.0, 36, AimbotRules.AIM_SCAN_STEP), 1.0e-9);
    }

    @Test
    void downwardScanIsCappedByLayerBudget() {
        // 完全遮挡时的射线护栏：层数不超过 AIM_SCAN_MAX_LAYERS
        int count = AimbotRules.scanLayerCount(76.0, 64.0, AimbotRules.AIM_SCAN_STEP,
                AimbotRules.AIM_SCAN_MAX_LAYERS);
        assertEquals(AimbotRules.AIM_SCAN_MAX_LAYERS, count);
    }

    @Test
    void downwardScanNeverLeavesTheBox() {
        // 起点低于箱底（异常输入）时退化为单层，且层高不会穿到箱底以下
        assertEquals(1, AimbotRules.scanLayerCount(64.0, 64.0, AimbotRules.AIM_SCAN_STEP, 48));
        assertEquals(0, AimbotRules.scanLayerCount(63.9, 64.0, AimbotRules.AIM_SCAN_STEP, 48));
        assertEquals(64.0, AimbotRules.scanLayerY(64.02, 64.0, 5, AimbotRules.AIM_SCAN_STEP), 1.0e-9);
    }

    @Test
    void upwardFallbackStartsAboveThePreferredPointAndStopsAtBoxTop() {
        // 坏爆头首选 0.65（脚 64.00 → 65.30）：上方最多 0.70 格可扫 = 14 层
        assertEquals(14, AimbotRules.upwardLayerCount(65.30, 66.0, AimbotRules.AIM_SCAN_STEP, 48));
        assertEquals(65.35, AimbotRules.upwardLayerY(65.30, 66.0, 1, AimbotRules.AIM_SCAN_STEP), 1.0e-9);
        assertEquals(66.00, AimbotRules.upwardLayerY(65.30, 66.0, 99, AimbotRules.AIM_SCAN_STEP), 1.0e-9);
        // 首选点已经贴箱顶（巨人 0.999）→ 没有上方兜底可言
        assertEquals(0, AimbotRules.upwardLayerCount(75.98, 75.99, AimbotRules.AIM_SCAN_STEP, 48));
    }

    // ---- 水平兜底三方向（垂线 + 左右各 45°）----

    @Test
    void fallbackDirectionsAreThreeUnitVectorsFortyFiveDegreesApart() {
        // 视线朝 +x，垂线取 (0,1)
        double[] dirs = AimbotRules.fallbackDirections(0.0, 1.0, 1.0, 0.0);
        assertEquals(6, dirs.length);
        assertEquals(0.0, dirs[0], 1.0e-9);
        assertEquals(1.0, dirs[1], 1.0e-9);
        double half = Math.sqrt(0.5);
        assertEquals(half, dirs[2], 1.0e-9);           // 垂线向视线方向转 45°
        assertEquals(half, dirs[3], 1.0e-9);
        assertEquals(-half, dirs[4], 1.0e-9);          // 另一侧 45°
        assertEquals(half, dirs[5], 1.0e-9);
        for (int i = 0; i + 1 < dirs.length; i += 2) {
            assertEquals(1.0, Math.hypot(dirs[i], dirs[i + 1]), 1.0e-9,
                    "第 " + (i / 2) + " 个方向应为单位向量");
        }
        assertEquals(Math.cos(Math.toRadians(45.0)), dirs[0] * dirs[2] + dirs[1] * dirs[3], 1.0e-9);
    }

    @Test
    void fallbackDirectionsRotateWithTheViewDirection() {
        // 视线朝 +z，垂线 (1,0)：两个 45° 方向落在 x±z 的对角线上
        double[] dirs = AimbotRules.fallbackDirections(1.0, 0.0, 0.0, 1.0);
        double half = Math.sqrt(0.5);
        assertEquals(1.0, dirs[0], 1.0e-9);
        assertEquals(0.0, dirs[1], 1.0e-9);
        assertEquals(half, dirs[2], 1.0e-9);
        assertEquals(half, dirs[3], 1.0e-9);
        assertEquals(half, dirs[4], 1.0e-9);
        assertEquals(-half, dirs[5], 1.0e-9);
    }

    /* ==================== 窗优先模式（2026-09-19 定稿口径） ==================== */

    @Test
    void windowPriorityModesMapToWindowsAndAnchors() {
        assertTrue(AimbotRules.priorityWindows(AimbotRules.WP_OFF).isEmpty());
        assertEquals(java.util.Set.of("P2", "P3", "P4"),
                AimbotRules.priorityWindows(AimbotRules.WP_P234));
        // P5+MID（2026-09-19 扩展）：P5 窗 + UFO 4 口 MID 怪
        assertEquals(java.util.Set.of("P5", "MID"), AimbotRules.priorityWindows(AimbotRules.WP_P5));
        assertEquals(java.util.Set.of("P1", "ULT"),
                AimbotRules.priorityWindows(AimbotRules.WP_P1_ULT));
        assertEquals(java.util.Set.of("ALT"), AimbotRules.priorityWindows(AimbotRules.WP_ALT));

        // 傀儡锚点：P234 → P4(-10,-6) r15；P1+ULT → P5(22,14) r10（原 ULT 点，2026-09-19 改）；P5/ALT/关 → 无。
        assertArrayEquals(new double[]{-10.0, -6.0, 15.0},
                AimbotRules.golemAnchor(AimbotRules.WP_P234), 1.0e-9);
        assertArrayEquals(new double[]{22.0, 14.0, 10.0},
                AimbotRules.golemAnchor(AimbotRules.WP_P1_ULT), 1.0e-9);
        assertNull(AimbotRules.golemAnchor(AimbotRules.WP_P5));
        assertNull(AimbotRules.golemAnchor(AimbotRules.WP_ALT));
        assertNull(AimbotRules.golemAnchor(AimbotRules.WP_OFF));

        for (int mode = AimbotRules.WP_OFF; mode <= AimbotRules.WP_ALT; mode++) {
            assertTrue(AimbotRules.isWindowPriorityMode(mode));
        }
        assertFalse(AimbotRules.isWindowPriorityMode(AimbotRules.WP_ALT + 1));
        assertFalse(AimbotRules.isWindowPriorityMode(-1));

        // HUD 简写（用户定稿 2026-09-19）：P234 / P5M / P1U / ALT；关闭不显示（null）。
        assertEquals("P234", AimbotRules.windowPriorityShort(AimbotRules.WP_P234));
        assertEquals("P5M", AimbotRules.windowPriorityShort(AimbotRules.WP_P5));
        assertEquals("P1U", AimbotRules.windowPriorityShort(AimbotRules.WP_P1_ULT));
        assertEquals("ALT", AimbotRules.windowPriorityShort(AimbotRules.WP_ALT));
        assertNull(AimbotRules.windowPriorityShort(AimbotRules.WP_OFF));
    }

    @Test
    void golemAnchorUsesPlanarDistance() {
        double[] anchor = {-10.0, -6.0, 15.0};
        // 垂直高度差不影响水平判定（y 不参与）。
        assertTrue(AimbotRules.withinGolemAnchor(anchor, -10.0, -6.0));
        assertTrue(AimbotRules.withinGolemAnchor(anchor, 5.0, -6.0));
        assertTrue(AimbotRules.withinGolemAnchor(anchor, -10.0, 9.0));
        assertFalse(AimbotRules.withinGolemAnchor(anchor, 6.0, -6.0));
        assertFalse(AimbotRules.withinGolemAnchor(null, -10.0, -6.0));
    }

    @Test
    void golemTagsCoverTheFourRealSpawnPoints() {
        // 4 个固定刷点（18 359 条 mob_spawn 全量统计，2026-09-19）全部命中，且互不串标。
        assertEquals(AimbotRules.GOLEM_TAG_ULT, AimbotRules.golemTagFor(20.5, 19.5));
        assertEquals(AimbotRules.GOLEM_TAG_RC, AimbotRules.golemTagFor(-19.5, 30.5));
        assertEquals(AimbotRules.GOLEM_TAG_ENT1, AimbotRules.golemTagFor(-9.5, 4.5));
        assertEquals(AimbotRules.GOLEM_TAG_ENT2, AimbotRules.golemTagFor(0.5, -7.5));
        // 容差 3 格：圈内命中、圈外不命中（坐标同步有误差时仍要打上标）。
        assertEquals(AimbotRules.GOLEM_TAG_ULT, AimbotRules.golemTagFor(20.5, 19.5 - 2.9));
        assertNull(AimbotRules.golemTagFor(20.5, 19.5 - 3.1));
        // 窗外零散出生的傀儡（百余条记录）不打标。
        assertNull(AimbotRules.golemTagFor(0.0, 0.0));
        assertNull(AimbotRules.golemTagFor(-22.0, 16.0));
    }

    @Test
    void p234LadderPutsRcGolemAboveWindowsAndEntGolems() {
        // ① RC-G 傀儡压过窗怪
        assertEquals(AimbotRules.RANK_MODE_GIANT, AimbotRules.priorityRank(
                AimbotRules.WP_P234, null, AimbotRules.GOLEM_TAG_RC, false, false, false, false));
        // ② 窗怪
        assertEquals(AimbotRules.RANK_WINDOW, AimbotRules.priorityRank(
                AimbotRules.WP_P234, "P3", null, false, false, false, false));
        // ③ ENT-G1/G2 傀儡（窗怪清完才轮到）
        assertEquals(AimbotRules.RANK_WINDOW_GOLEM, AimbotRules.priorityRank(
                AimbotRules.WP_P234, null, AimbotRules.GOLEM_TAG_ENT1, false, false, false, false));
        assertEquals(AimbotRules.RANK_WINDOW_GOLEM, AimbotRules.priorityRank(
                AimbotRules.WP_P234, null, AimbotRules.GOLEM_TAG_ENT2, false, false, false, false));
        // 别的模式的标在 P234 里不产生优先档
        assertEquals(AimbotRules.RANK_DEMOTED, AimbotRules.priorityRank(
                AimbotRules.WP_P234, null, AimbotRules.GOLEM_TAG_ULT, false, false, true, false));
        // 锚点兜底仍在：未打标但落在 P4 r15 圈里 → 与 ENT-G 同档
        assertEquals(AimbotRules.RANK_WINDOW_GOLEM, AimbotRules.priorityRank(
                AimbotRules.WP_P234, null, null, false, true, false, false));
    }

    @Test
    void p1uLadderPutsUltGolemBelowItsWindows() {
        // 窗怪（P1/ULT）优先于 ULT-G 傀儡
        assertEquals(AimbotRules.RANK_WINDOW, AimbotRules.priorityRank(
                AimbotRules.WP_P1_ULT, "P1", null, false, false, false, false));
        assertEquals(AimbotRules.RANK_WINDOW, AimbotRules.priorityRank(
                AimbotRules.WP_P1_ULT, "ULT", null, false, false, false, false));
        assertEquals(AimbotRules.RANK_WINDOW_GOLEM, AimbotRules.priorityRank(
                AimbotRules.WP_P1_ULT, null, AimbotRules.GOLEM_TAG_ULT, false, false, false, false));
        // RC-G / ENT-G 在 P1+ULT 里不参与优先档
        assertEquals(AimbotRules.RANK_DEMOTED, AimbotRules.priorityRank(
                AimbotRules.WP_P1_ULT, null, AimbotRules.GOLEM_TAG_RC, false, false, true, false));
        // P5+MID 不受傀儡标影响（它只有巨人档 + MID/P5 窗怪档）；旧签名未判空中，MID 按落地算
        assertEquals(AimbotRules.RANK_WINDOW_MID_GROUND, AimbotRules.priorityRank(
                AimbotRules.WP_P5, "MID", AimbotRules.GOLEM_TAG_ULT, false, false, false, false));
    }

    @Test
    void sweepGateOnlyAppliesToTheModesOwnWindowMobs() {
        // P234：P2/P3/P4 怪 3 秒
        assertEquals(3000, AimbotRules.sweepGateMs(AimbotRules.WP_P234, "P2"));
        assertEquals(3000, AimbotRules.sweepGateMs(AimbotRules.WP_P234, "P3"));
        assertEquals(3000, AimbotRules.sweepGateMs(AimbotRules.WP_P234, "P4"));
        // P1+ULT：P1/ULT 怪 2 秒
        assertEquals(2000, AimbotRules.sweepGateMs(AimbotRules.WP_P1_ULT, "P1"));
        assertEquals(2000, AimbotRules.sweepGateMs(AimbotRules.WP_P1_ULT, "ULT"));
        // 其他任何东西都不门控（用户口径：仅限于对应的窗怪，其他怪不受影响）
        assertEquals(0, AimbotRules.sweepGateMs(AimbotRules.WP_P234, "P5"));
        assertEquals(0, AimbotRules.sweepGateMs(AimbotRules.WP_P234, "MID"));
        assertEquals(0, AimbotRules.sweepGateMs(AimbotRules.WP_P234, null));
        assertEquals(0, AimbotRules.sweepGateMs(AimbotRules.WP_P1_ULT, "ULT-G"));
        assertEquals(0, AimbotRules.sweepGateMs(AimbotRules.WP_P5, "P5"));
        assertEquals(0, AimbotRules.sweepGateMs(AimbotRules.WP_ALT, "ALT"));
        assertEquals(0, AimbotRules.sweepGateMs(AimbotRules.WP_OFF, "P2"));
    }

    @Test
    void bruteChainHoldsAWindowMobThroughItsBirthGate() {
        int[] ids = {11, 22, 33};
        double[] yaw = {-20.0, -2.0, 15.0};
        // 22 是刚出窗的怪：出生门控到 5000ms，dwell 100ms 早已到期 → 仍然保持 22（不扫射）
        long[] gate = {0L, 5000L, 0L};
        AimbotRules.BruteChainPick held = AimbotRules.bruteChainDecision(ids, yaw,
                22, -2.0, AimbotRules.BRUTE_DIR_RIGHT, 45.0, 1_000L, 1_000L, 100, gate);
        assertEquals(22, held.entityId());
        assertFalse(held.moved());
        assertEquals(5_000L, held.holdUntilMs(), "门控把保持截止抬到出生 3 秒后");
        // 门控一过 → 立刻恢复按 dwell 轮转
        AimbotRules.BruteChainPick hop = AimbotRules.bruteChainDecision(ids, yaw,
                22, -2.0, AimbotRules.BRUTE_DIR_RIGHT, 45.0, 5_000L, 5_000L, 100, gate);
        assertEquals(33, hop.entityId());
        assertTrue(hop.moved());
        assertEquals(5_100L, hop.holdUntilMs());
        // 切到的新目标自己带门控 → 它的保持截止同样被抬起（刚出窗的那只被咬住打完）
        long[] gateOnNext = {0L, 0L, 6_000L};
        AimbotRules.BruteChainPick gated = AimbotRules.bruteChainDecision(ids, yaw,
                22, -2.0, AimbotRules.BRUTE_DIR_RIGHT, 45.0, 5_000L, 5_000L, 100, gateOnNext);
        assertEquals(33, gated.entityId());
        assertEquals(6_000L, gated.holdUntilMs());
        // 没有门控的池子行为不变（等价旧签名）
        AimbotRules.BruteChainPick plain = AimbotRules.bruteChainDecision(ids, yaw,
                22, -2.0, AimbotRules.BRUTE_DIR_RIGHT, 45.0, 5_000L, 5_000L, 100,
                new long[]{0L, 0L, 0L});
        assertEquals(33, plain.entityId());
        assertEquals(5_100L, plain.holdUntilMs());
    }

    @Test
    void priorityRankOrdersWindowsAboveGolemsAboveIgnored() {
        // P5+MID 模式巨人档（默认优先；Clown 开启时调用方传 false）压过窗怪与一切。
        assertEquals(AimbotRules.RANK_MODE_GIANT, AimbotRules.priorityRank(
                AimbotRules.WP_P5, null, true, false, false, false));
        // 窗怪档压过一切（含类型忽略：P2 的 TOO/Baby 也是窗怪）。
        assertEquals(AimbotRules.RANK_WINDOW, AimbotRules.priorityRank(
                AimbotRules.WP_P234, "P2", false, false, false, false));
        assertEquals(AimbotRules.RANK_WINDOW, AimbotRules.priorityRank(
                AimbotRules.WP_P234, "P2", false, false, true, true));
        // P5+MID 的 MID 怪（birthWindowIdOf 返回 "MID"）未判空中按落地算，反超 P5 窗怪（2026-09-22）。
        assertEquals(AimbotRules.RANK_WINDOW_MID_GROUND, AimbotRules.priorityRank(
                AimbotRules.WP_P5, "MID", false, false, false, false));
        assertEquals(AimbotRules.RANK_WINDOW, AimbotRules.priorityRank(
                AimbotRules.WP_P5, "P5", false, false, false, false));
        // 模式窗集合外的窗 id（如 P2 在 P5+MID 模式下）不算窗怪档。
        assertEquals(AimbotRules.RANK_NORMAL, AimbotRules.priorityRank(
                AimbotRules.WP_P5, "P2", false, false, false, false));
        // 非窗怪的铁傀儡（锚点内）档位居窗怪之后。
        assertEquals(AimbotRules.RANK_WINDOW_GOLEM, AimbotRules.priorityRank(
                AimbotRules.WP_P234, null, false, true, false, false));
        assertEquals(AimbotRules.RANK_WINDOW_GOLEM, AimbotRules.priorityRank(
                AimbotRules.WP_P1_ULT, null, false, true, false, false));
        // 普通怪 → 降级（高处怪/Clown 模式巨人）→ 忽略（baby/TOO/傀儡/史莱姆）。
        assertEquals(AimbotRules.RANK_NORMAL, AimbotRules.priorityRank(
                AimbotRules.WP_P234, null, false, false, false, false));
        assertEquals(AimbotRules.RANK_DEMOTED, AimbotRules.priorityRank(
                AimbotRules.WP_P234, null, false, false, true, false));
        assertEquals(AimbotRules.RANK_IGNORED, AimbotRules.priorityRank(
                AimbotRules.WP_P234, null, false, false, false, true));
        // 模式关闭：窗 id/模式巨人都不再产生优先档，忽略怪仍落末位档（修复独立于模式）。
        assertEquals(AimbotRules.RANK_NORMAL, AimbotRules.priorityRank(
                AimbotRules.WP_OFF, "P2", false, false, false, false));
        assertEquals(AimbotRules.RANK_IGNORED, AimbotRules.priorityRank(
                AimbotRules.WP_OFF, null, false, false, false, true));
    }

    @Test
    void midSplitsIntoGroundAndAirTiersForP5ModeOnly() {
        // 2026-09-22 定稿：MID 落地怪反超 P5；MID 空中怪（坠落/悬空）降到 P5 之后。
        // 八档链：巨人 < MID 落地 < P5 窗怪 < MID 空中 < 窗傀儡 < 普通 < 降级 < 忽略。
        assertTrue(AimbotRules.RANK_MODE_GIANT < AimbotRules.RANK_WINDOW_MID_GROUND);
        assertTrue(AimbotRules.RANK_WINDOW_MID_GROUND < AimbotRules.RANK_WINDOW);
        assertTrue(AimbotRules.RANK_WINDOW < AimbotRules.RANK_WINDOW_MID_AIR);
        assertTrue(AimbotRules.RANK_WINDOW_MID_AIR < AimbotRules.RANK_WINDOW_GOLEM);
        assertTrue(AimbotRules.RANK_WINDOW_GOLEM < AimbotRules.RANK_NORMAL);
        assertTrue(AimbotRules.RANK_NORMAL < AimbotRules.RANK_DEMOTED);
        assertTrue(AimbotRules.RANK_DEMOTED < AimbotRules.RANK_IGNORED);

        assertEquals(AimbotRules.RANK_WINDOW_MID_GROUND, AimbotRules.priorityRank(
                AimbotRules.WP_P5, "MID", null, false, false, false, false, false));
        assertEquals(AimbotRules.RANK_WINDOW_MID_AIR, AimbotRules.priorityRank(
                AimbotRules.WP_P5, "MID", null, false, false, true, false, false));
        // P5 窗怪没有空中/落地之分，midAir 参数对它无效。
        assertEquals(AimbotRules.RANK_WINDOW, AimbotRules.priorityRank(
                AimbotRules.WP_P5, "P5", null, false, false, true, false, false));
        // MID 空中仍在窗怪族内：压过高处降级与类型忽略（与原窗怪语义一致）。
        assertEquals(AimbotRules.RANK_WINDOW_MID_AIR, AimbotRules.priorityRank(
                AimbotRules.WP_P5, "MID", null, false, false, true, true, true));
        // 其他模式的 MID 怪根本不是窗怪：midAir 不产生任何窗档（P234 完全不受影响）。
        assertEquals(AimbotRules.RANK_NORMAL, AimbotRules.priorityRank(
                AimbotRules.WP_P234, "MID", null, false, false, true, false, false));
        assertEquals(AimbotRules.RANK_IGNORED, AimbotRules.priorityRank(
                AimbotRules.WP_P234, "MID", null, false, false, true, false, true));
    }

    @Test
    void midAirNeedsMidTagAndFiveBlockClearance() {
        double threshold = 5.0;
        // 高于 MID 地面(y=76) 5 格内按落地算，超过才算空中（玩家站同层，隔离出地面基准）。
        assertFalse(AimbotRules.isMidAir("MID",
                AimbotRules.MID_GROUND_Y + 5.0, AimbotRules.MID_GROUND_Y, threshold));
        assertTrue(AimbotRules.isMidAir("MID",
                AimbotRules.MID_GROUND_Y + 5.5, AimbotRules.MID_GROUND_Y, threshold));
        // 玩家在高台时：怪高于玩家 5 格也算空中。
        assertTrue(AimbotRules.isMidAir("MID", 74.0, 68.0, threshold));
        // 玩家站得比怪高不把怪算空中（只判怪高于基准）。
        assertFalse(AimbotRules.isMidAir("MID", 72.0, 90.0, threshold));
        // 非 MID 窗怪（P5/未归档）永不判空中。
        assertFalse(AimbotRules.isMidAir("P5", 105.0, 72.0, threshold));
        assertFalse(AimbotRules.isMidAir(null, 105.0, 72.0, threshold));
    }
}
