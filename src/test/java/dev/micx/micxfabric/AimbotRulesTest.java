package dev.micx.micxfabric;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        assertEquals(AimbotRules.GROUP_PRIORITY, AimbotRules.instaGroupRank(false, false, false));
        // baby 与史莱姆/岩浆怪都降到「普通怪之后」档，且同档
        assertEquals(AimbotRules.GROUP_DEPRIORITIZED, AimbotRules.instaGroupRank(true, false, false));
        assertEquals(AimbotRules.GROUP_DEPRIORITIZED, AimbotRules.instaGroupRank(false, true, false));
        assertEquals(AimbotRules.instaGroupRank(true, false, false),
                AimbotRules.instaGroupRank(false, true, false));
        assertTrue(AimbotRules.instaGroupRank(false, false, false)
                > AimbotRules.instaGroupRank(true, true, false));
        // BRUTE 扫射生效时 baby 恢复最高组
        assertEquals(AimbotRules.GROUP_BABY_FIRST, AimbotRules.instaGroupRank(true, false, true));
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
    void bruteSweepGatesByRoundAndStaysInsideTheFov() {
        // 起始回合 53：之前不生效，53 起生效，未知回合不门控
        assertFalse(AimbotRules.bruteSweepAllowed(52, 53));
        assertTrue(AimbotRules.bruteSweepAllowed(53, 53));
        assertTrue(AimbotRules.bruteSweepAllowed(80, 53));
        assertTrue(AimbotRules.bruteSweepAllowed(0, 53));
        assertTrue(AimbotRules.bruteSweepAllowed(-1, 53));
        // FOV 半角之内才参与扫射，不做 360° 乱扫
        assertTrue(AimbotRules.bruteSweepInFov(-59.9, 60.0));
        assertTrue(AimbotRules.bruteSweepInFov(60.0, 60.0));
        assertFalse(AimbotRules.bruteSweepInFov(60.1, 60.0));
        assertFalse(AimbotRules.bruteSweepInFov(120.0, 60.0));
        assertFalse(AimbotRules.bruteSweepInFov(Double.NaN, 60.0));
        // 0.2.70 起默认半角收到 45°
        assertTrue(AimbotRules.bruteSweepInFov(44.9, 45.0));
        assertTrue(AimbotRules.bruteSweepInFov(-45.0, 45.0));
        assertFalse(AimbotRules.bruteSweepInFov(45.1, 45.0));
        assertFalse(AimbotRules.bruteSweepInFov(60.0, 45.0));
    }

    @Test
    void coneGoesStaleWhenTooFewTargetsOrTheCrosshairLeavesIt() {
        // 0/1 只都没得扫（1 只时游标只有一个落点，推进也还是它）→ 过期
        assertTrue(AimbotRules.bruteSweepConeStale(0, 2, 0.0, 45.0));
        assertTrue(AimbotRules.bruteSweepConeStale(1, 2, 0.0, 45.0));
        assertFalse(AimbotRules.bruteSweepConeStale(2, 2, 0.0, 45.0));
        assertFalse(AimbotRules.bruteSweepConeStale(6, 2, 30.0, 45.0));
        // 准星自己跑出锥体（玩家转身看别处）→ 过期；刚好压在边界不算
        assertFalse(AimbotRules.bruteSweepConeStale(3, 2, 45.0, 45.0));
        assertTrue(AimbotRules.bruteSweepConeStale(3, 2, 45.1, 45.0));
        assertTrue(AimbotRules.bruteSweepConeStale(3, 2, 120.0, 45.0));
        // 角度缺失（NaN）不算过期：宁可维持现状也不要因为一次坏数据把锥体重锚
        assertFalse(AimbotRules.bruteSweepConeStale(3, 2, Double.NaN, 45.0));
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

    @Test
    void bruteSweepAdvancesLeftToRightThenRestarts() {
        double[] yaw = {-30.0, -5.0, 12.0, 40.0};
        assertEquals(0, AimbotRules.bruteSweepAdvance(yaw, Double.NaN, 0.25));
        assertEquals(0, AimbotRules.bruteSweepAdvance(yaw, -45.0, 0.25));
        assertEquals(1, AimbotRules.bruteSweepAdvance(yaw, -30.0, 0.25));
        assertEquals(2, AimbotRules.bruteSweepAdvance(yaw, -5.0, 0.25));
        assertEquals(3, AimbotRules.bruteSweepAdvance(yaw, 12.0, 0.25));
        assertEquals(0, AimbotRules.bruteSweepAdvance(yaw, 40.0, 0.25));
        assertEquals(-1, AimbotRules.bruteSweepAdvance(new double[0], 0.0, 0.25));
        assertEquals(-1, AimbotRules.bruteSweepAdvance(null, 0.0, 0.25));
    }

    /* ---- BRUTE 扫射状态机：这组是「用户实测两次回归」的离线护栏 ----
     * 回归 1：扫射在游戏里其实没工作（锥体、保活、推进三者只要一处错就退化单锁）。
     * 回归 2：目标死亡后用上一落点推进会把游标停在同一只上，扫射退化成单锁。
     */

    @Test
    void bruteSweepDecisionHoldsLiveTargetThenAdvancesRightOnDeath() {
        double eps = 0.25;
        int[] ids = {11, 22, 33};
        double[] yaw = {-20.0, -2.0, 15.0};

        // 目标还活着且在锥内 → 保持，不换目标
        AimbotRules.BruteSweepPick held = AimbotRules.bruteSweepDecision(ids, yaw,
                22, -2.0, 1_000L, AimbotRules.BRUTE_HOLD_FOREVER, 0, eps);
        assertEquals(22, held.entityId());
        assertFalse(held.advanced());
        assertEquals(-2.0, held.yawOff(), 1.0E-9);

        // 22 死亡（从候选里消失）→ 沿锥体往右推进到 33，绝不重新选回同一只
        AimbotRules.BruteSweepPick next = AimbotRules.bruteSweepDecision(new int[]{11, 33},
                new double[]{-20.0, 15.0}, 22, -2.0, 1_000L,
                AimbotRules.BRUTE_HOLD_FOREVER, 0, eps);
        assertEquals(33, next.entityId());
        assertTrue(next.advanced());
    }

    @Test
    void bruteSweepHopsToTheNextTargetEveryDwellRegardlessOfDeath() {
        // 扫射的本义（用户 2026-09-15 定稿）：每只最多停 100ms，到点立刻换下一只，
        // **不管有没有打死**；死亡/掉出锥体只是提前换。0.2.90 曾把默认改成
        // 「0 = 只在死亡时推进」，扫射因此退化成锁单只（用户实测「完全不扫」）。
        double eps = 0.25;
        int dwell = 100;
        int[] ids = {11, 22, 33};
        double[] yaw = {-20.0, -2.0, 15.0};

        // t=0 首次决策 → 锁定最左那只，并给出停留截止
        AimbotRules.BruteSweepPick first = AimbotRules.bruteSweepDecision(ids, yaw,
                -1, Double.NaN, 0L, 0L, dwell, eps);
        assertEquals(11, first.entityId());
        assertTrue(first.advanced());
        assertEquals(dwell, first.holdUntilMs());

        // 未到期 → 保持（三只都还活着也一样）
        AimbotRules.BruteSweepPick holding = AimbotRules.bruteSweepDecision(ids, yaw,
                11, yaw[0], 50L, dwell, dwell, eps);
        assertEquals(11, holding.entityId());
        assertFalse(holding.advanced());

        // 一到点 → 换下一只，与「有没有死」无关
        AimbotRules.BruteSweepPick second = AimbotRules.bruteSweepDecision(ids, yaw,
                11, yaw[0], dwell, dwell, dwell, eps);
        assertEquals(22, second.entityId());
        assertTrue(second.advanced());
        assertEquals(2L * dwell, second.holdUntilMs());

        // 继续到点 → 第三只
        AimbotRules.BruteSweepPick third = AimbotRules.bruteSweepDecision(ids, yaw,
                22, yaw[1], 2L * dwell, 2L * dwell, dwell, eps);
        assertEquals(33, third.entityId());
        assertTrue(third.advanced());

        // 扫到最右端再到期 → 绕回最左，一圈一圈地扫
        AimbotRules.BruteSweepPick wrapped = AimbotRules.bruteSweepDecision(ids, yaw,
                33, yaw[2], 3L * dwell, 3L * dwell, dwell, eps);
        assertEquals(11, wrapped.entityId());
        assertTrue(wrapped.advanced());
    }

    @Test
    void bruteSweepDecisionWrapsBackToLeftmostWhenNothingToTheRight() {
        // 最右端那只死后没有更右的目标 → 回到锥体最左端（重新从左扫）
        AimbotRules.BruteSweepPick pick = AimbotRules.bruteSweepDecision(new int[]{11},
                new double[]{-20.0}, 33, 15.0, 5_000L, AimbotRules.BRUTE_HOLD_FOREVER, 0, 0.25);
        assertEquals(11, pick.entityId());
        assertEquals(-20.0, pick.yawOff(), 1.0E-9);
        assertTrue(pick.advanced());
    }

    @Test
    void bruteSweepDecisionHoldGuardIsSafeValveNotAWait() {
        int[] ids = {11, 22, 33};
        double[] yaw = {-20.0, -2.0, 15.0};

        // dwell=0（默认）：停留不设上限，保持分支一直有效
        AimbotRules.BruteSweepPick unlimited = AimbotRules.bruteSweepDecision(ids, yaw,
                22, -2.0, 60_000L, AimbotRules.BRUTE_HOLD_FOREVER, 0, 0.25);
        assertEquals(22, unlimited.entityId());
        assertFalse(unlimited.advanced());

        // dwell=200ms 的安全阀：到期后即使目标还活着也换人（防僵在打不死的怪上）
        AimbotRules.BruteSweepPick guard = AimbotRules.bruteSweepDecision(ids, yaw,
                22, -2.0, 1_000L, 1_200L, 200, 0.25);
        assertEquals(22, guard.entityId());
        assertFalse(guard.advanced());

        AimbotRules.BruteSweepPick expired = AimbotRules.bruteSweepDecision(ids, yaw,
                22, -2.0, 1_300L, 1_200L, 200, 0.25);
        assertEquals(33, expired.entityId());
        assertTrue(expired.advanced());
        assertEquals(1_500L, expired.holdUntilMs());

        // dwell<=0 推进时重新签发的截止是「不限」
        assertEquals(AimbotRules.BRUTE_HOLD_FOREVER,
                AimbotRules.bruteSweepDecision(ids, yaw, -1, Double.NaN, 0L, 0L, 0, 0.25)
                        .holdUntilMs());
    }

    @Test
    void bruteSweepDecisionEmptyConeFallsBackToSingleLock() {
        assertEquals(-1, AimbotRules.BRUTE_SWEEP_NONE.entityId());
        assertFalse(AimbotRules.BRUTE_SWEEP_NONE.advanced());
        assertFalse(AimbotRules.bruteSweepDecision(new int[0], new double[0],
                5, 0.0, 0L, AimbotRules.BRUTE_HOLD_FOREVER, 0, 0.25).advanced());
        assertFalse(AimbotRules.bruteSweepDecision(null, null,
                5, 0.0, 0L, AimbotRules.BRUTE_HOLD_FOREVER, 0, 0.25).advanced());
        // 长度不一致视为退化输入，不得越界
        assertFalse(AimbotRules.bruteSweepDecision(new int[]{1, 2}, new double[]{1.0},
                5, 0.0, 0L, AimbotRules.BRUTE_HOLD_FOREVER, 0, 0.25).advanced());
    }

    @Test
    void bruteSweepConeSlotsFiltersByAnchorThenSortsAscending() {
        double[] offsets = new double[8];
        int[] slots = AimbotRules.bruteSweepConeSlots(0.0, 30.0,
                new double[]{40.0, -10.0, 5.0, -50.0, 25.0}, offsets);
        assertArrayEquals(new int[]{1, 2, 4}, slots);
        assertEquals(-10.0, offsets[0], 1.0E-9);
        assertEquals(5.0, offsets[1], 1.0E-9);
        assertEquals(25.0, offsets[2], 1.0E-9);

        // 跨 0°/360°：锚定 350° 时 349° 在左侧、1°/20° 在右侧
        double[] wrapped = new double[3];
        int[] wslots = AimbotRules.bruteSweepConeSlots(350.0, 30.0,
                new double[]{349.0, 1.0, 20.0}, wrapped);
        assertArrayEquals(new int[]{0, 1, 2}, wslots);
        assertEquals(-1.0, wrapped[0], 1.0E-9);
        assertEquals(11.0, wrapped[1], 1.0E-9);
        assertEquals(30.0, wrapped[2], 1.0E-9);

        // 空候选 / 全部落在锥体外 → 空结果（调用方回落到单锁）
        assertEquals(0, AimbotRules.bruteSweepConeSlots(0.0, 30.0, new double[0],
                new double[0]).length);
        assertEquals(0, AimbotRules.bruteSweepConeSlots(0.0, 30.0,
                new double[]{120.0, -120.0}, new double[2]).length);
    }

    /**
     * 2026-09-14 回归防护：目标死亡后必须沿锥体推进，不能卡在原地。
     *
     * <p>死因链路：锁定的目标死亡 → 下一帧不再命中保持分支 → 用上一落点推进。
     * 若推进规则让游标退回同一只（或回绕到最左端反复选同一侧），
     * 表现就是「完全不扫射」（用户 2026-09-14 实测）。
     */
    @Test
    void bruteSweepCursorAdvancesPastDeadTargetInsteadOfReselecting() {
        double[] yaw = {-30.0, -5.0, 12.0, 40.0};
        double eps = 0.25;
        // 0 号位（-30）死亡：prevYawOff = -30，advance 内部 +eps → 必须落到 1 号位
        assertEquals(1, AimbotRules.bruteSweepAdvance(yaw, -30.0, eps));
        // 1 号位死亡 → 落到 2 号位（不能停在 1）
        assertEquals(2, AimbotRules.bruteSweepAdvance(yaw, -5.0, eps));
        // 2 号位死亡 → 落到 3 号位
        assertEquals(3, AimbotRules.bruteSweepAdvance(yaw, 12.0, eps));
        // 最右端（3 号位）死亡 → 越过右端回绕到最左端，重新开始一轮
        assertEquals(0, AimbotRules.bruteSweepAdvance(yaw, 40.0, eps));
        // 游标在最小项之前（首个目标首次入选）→ 仍从最左端开始
        assertEquals(0, AimbotRules.bruteSweepAdvance(yaw, -45.0, eps));
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
        // 新签名：groupRank(prioClown, prioGiant, babyFirst, baby, clown, giant)
        // baby 不再有独立开关，默认降到「普通怪之后」档
        assertEquals(AimbotRules.GROUP_DEPRIORITIZED,
                AimbotRules.groupRank(false, false, false, true, false, false));
        // BRUTE 扫射生效时 baby 恢复最高组
        assertEquals(AimbotRules.GROUP_BABY_FIRST,
                AimbotRules.groupRank(false, false, true, true, false, false));
        assertEquals(AimbotRules.GROUP_PRIORITY,
                AimbotRules.groupRank(true, false, false, false, true, false));
        // 用户定稿 2026-09-16：巨人降档改挂 Clown 模式（两个模式的对称语义见
        // clownAndGiantModesAreSymmetric）。
        assertEquals(AimbotRules.GROUP_GIANT_BACKUP,
                AimbotRules.groupRank(true, false, false, false, false, true));
        // 末位档排序：巨人 < 头顶高处 < baby < 普通怪（数值越小越晚锁）
        assertTrue(AimbotRules.GROUP_GIANT_BACKUP < AimbotRules.GROUP_HIGH_ABOVE);
        assertTrue(AimbotRules.GROUP_HIGH_ABOVE < AimbotRules.GROUP_DEPRIORITIZED);
        // 普通怪仍是 0，baby 降级后严格低于普通怪
        assertEquals(0, AimbotRules.groupRank(false, false, false, false, false, false));
        assertTrue(AimbotRules.groupRank(false, false, false, false, false, false)
                > AimbotRules.groupRank(false, false, false, true, false, false));
        // 头顶高处（调用方 Math.min 合入）：即使 baby 降级也不越过它
        assertEquals(AimbotRules.GROUP_HIGH_ABOVE,
                Math.min(AimbotRules.groupRank(false, false, false, false, false, false),
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
                AimbotRules.groupRank(false, true, false, false, false, true));
        assertTrue(AimbotRules.groupRank(false, true, false, false, false, true) >= 0);
        // Giant 模式不影响小丑，也不影响普通怪 / baby
        assertEquals(0, AimbotRules.groupRank(false, true, false, false, true, false));
        assertEquals(0, AimbotRules.groupRank(false, true, false, false, false, false));
        assertEquals(AimbotRules.GROUP_DEPRIORITIZED,
                AimbotRules.groupRank(false, true, false, true, false, false));
        // Giant 模式下巨人不再是「末位」（末位档会让它被 preferred 池排除，永远不锁）
        assertNotEquals(AimbotRules.GROUP_GIANT_BACKUP,
                AimbotRules.groupRank(false, true, false, false, false, true));

        // Clown 模式：小丑首选，同时巨人被压到末位档（< 0 = 只在首选池全不可打时才扫）
        assertEquals(AimbotRules.GROUP_PRIORITY,
                AimbotRules.groupRank(true, false, false, false, true, false));
        int clownGiant = AimbotRules.groupRank(true, false, false, false, false, true);
        assertEquals(AimbotRules.GROUP_GIANT_BACKUP, clownGiant);
        assertTrue(clownGiant < 0, "Clown 模式下巨人必须落在降级池");
        // Clown 模式不影响普通怪 / baby
        assertEquals(0, AimbotRules.groupRank(true, false, false, false, false, false));
        assertEquals(AimbotRules.GROUP_DEPRIORITIZED,
                AimbotRules.groupRank(true, false, false, true, false, false));

        // 两个都不开：巨人按普通档参与（既不提前也不降级）
        assertEquals(0, AimbotRules.groupRank(false, false, false, false, false, true));

        // BRUTE 扫射的 baby 最高组对两个模式都生效
        assertEquals(AimbotRules.GROUP_BABY_FIRST,
                AimbotRules.groupRank(true, false, true, true, false, false));
        assertEquals(AimbotRules.GROUP_BABY_FIRST,
                AimbotRules.groupRank(false, true, true, true, false, false));
        // baby 与优先/降级目标同时在场时，扫射的 baby 仍压过巨人（扫射按空间顺序逐个清）
        assertTrue(AimbotRules.groupRank(false, true, true, true, false, false)
                > AimbotRules.groupRank(false, true, false, false, false, true));
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
}
