package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v0.2.11 块1/块2 构建器与色码对齐 Forge 的防回归（tasks.md T1-TR1..TR5 / TR8）。
 */
class ZombiesTacticalLinesTest {

    private static final Pattern CODE = Pattern.compile("\u00a7([0-9a-f])");

    /** T1-TR2：round=90 首行 = §6Round:§f90 §7| §bw<i>/<t> §fLS: 6 GIANT + clown + golem + TOO */
    @Test
    void roundLineForLsRoundMatchesForgeLayout() {
        String line = ZombiesAssistModule.buildRoundLine(90, 0, 6,
                ZombiesRoundData.roundMobs(90));
        // roundMobs(90) 经 normalize(=70) → "LS: 6 GIANT + clown + golem + TOO"
        assertEquals("\u00a76Round:\u00a7f90 \u00a77| \u00a7bw0/6 \u00a7fLS: 6 GIANT + clown + golem + TOO", line);
    }

    @Test
    void roundLineAppendsLastWaveMarkerAndHidesMobsWhenDisabled() {
        assertEquals("\u00a76Round:\u00a7f44 \u00a77| \u00a7bw7/7\u00a78(last)",
                ZombiesAssistModule.buildRoundLine(44, 7, 7, null));
        assertEquals("\u00a76Round:\u00a7f5", ZombiesAssistModule.buildRoundLine(5, 0, 0, null));
    }

    /** T1-TR3 语义的一部分：最后一波标记（回退行本身在 drawTacticalHud 内联，此处锁格式常量）。 */
    @Test
    void clearMobLineUsesForgeColorsAndMssFormat() {
        assertEquals("\u00a77\u6e05\u602a \u00a7c0:59 \u00a77\u540e\u602a\u7269\u6d88\u5931",
                ZombiesAssistModule.buildClearMobLine(59_999L));
        assertEquals("\u00a77\u6e05\u602a \u00a7e4:05 \u00a77\u540e\u602a\u7269\u6d88\u5931",
                ZombiesAssistModule.buildClearMobLine(245_999L));
        assertNull(ZombiesAssistModule.buildClearMobLine(0L));
        assertNull(ZombiesAssistModule.buildClearMobLine(-1L));
    }

    /** T1-TR4：波次预告三段（§d§lTOO / §2§lGiant / §5§lTOO+Giant + §7w..） */
    @Test
    void waveForecastLineMatchesForgeSegmentColors() {
        assertEquals("\u00a7d\u00a7lTOO\u00a7r \u00a7w2,3  \u00a72\u00a7lGiant\u00a7r \u00a7w4  \u00a75\u00a7lTOO+Giant\u00a7r \u00a7w5,6",
                ZombiesAssistModule.buildWaveForecastLine(
                        new int[]{2, 3}, new int[]{4}, new int[]{5, 6}));
        assertNull(ZombiesAssistModule.buildWaveForecastLine(
                new int[0], new int[0], new int[0]));
        assertEquals("\u00a7d\u00a7lTOO\u00a7r \u00a7w2,3",
                ZombiesAssistModule.buildWaveForecastLine(
                        new int[]{2, 3}, new int[0], new int[0]));
    }

    /** 下一波预警（块2）：TOO 优先于 Giant。 */
    @Test
    void nextWaveAlertPrefersTooOverGiant() {
        assertEquals("\u00a7c\u00a7l! TOO NEXT WAVE !",
                ZombiesAssistModule.buildNextWaveAlert(new int[]{2, 3}, new int[]{4},
                        new int[0], 1, 6));
        assertEquals("\u00a7c\u00a7l! TOO NEXT WAVE !",
                ZombiesAssistModule.buildNextWaveAlert(new int[0], new int[0],
                        new int[]{4, 5, 6}, 3, 6));
        assertEquals("\u00a72\u00a7l! GIANT NEXT WAVE !",
                ZombiesAssistModule.buildNextWaveAlert(new int[0], new int[]{4},
                        new int[0], 3, 6));
        assertNull(ZombiesAssistModule.buildNextWaveAlert(new int[]{2}, new int[0],
                new int[0], 2, 6));
        assertNull(ZombiesAssistModule.buildNextWaveAlert(new int[]{2}, new int[0],
                new int[0], 5, 6));
    }

    /** T1-TR5：roundTypeHint 七类全覆盖（Forge AAData 逐字符）。 */
    @Test
    void roundTypeHintCoversAllSevenForgeCategories() {
        assertEquals("\u00a7c\u00a7lLS ROUND \u00a7r\u00a77block + LR, 3+1", ZombiesRoundData.roundTypeHint(70));
        assertEquals("\u00a7c\u00a7lLS ROUND \u00a7r\u00a77block + LR, 3+1", ZombiesRoundData.roundTypeHint(90));
        assertEquals("\u00a76\u00a7lMEGA SLIME \u00a7r\u00a77reward round, free gold", ZombiesRoundData.roundTypeHint(25));
        assertEquals("\u00a7e\u00a7lSLIME+GIANT \u00a7r\u00a77block until giant", ZombiesRoundData.roundTypeHint(39));
        assertEquals("\u00a7a\u00a7lSLIME \u00a7r\u00a77grow! no insta kill", ZombiesRoundData.roundTypeHint(18));
        assertEquals("\u00a76FREE GOLD \u00a77squat cc", ZombiesRoundData.roundTypeHint(46));
        assertEquals("\u00a7d\u00a7lTOO \u00a7r\u00a77watch for The Old One", ZombiesRoundData.roundTypeHint(40));
        assertEquals("\u00a7bULT/ALT \u00a77squat + 3rd+LR", ZombiesRoundData.roundTypeHint(45));
        assertNull(ZombiesRoundData.roundTypeHint(1));
        assertNull(ZombiesRoundData.roundTypeHint(101));
        // normalize：81-89 映射 71-79（m≠0 → 60+m）
        assertEquals(ZombiesRoundData.roundTypeHint(70), ZombiesRoundData.roundTypeHint(80));
    }

    /** T1-TR1 语义：被移除的行不再出现在任何构建器产物中。 */
    @Test
    void removedLinesNeverReappear() {
        List<String> all = List.of(
                ZombiesAssistModule.buildRoundLine(90, 0, 6, ZombiesRoundData.roundMobs(90)),
                ZombiesAssistModule.buildClearMobLine(299_999L),
                ZombiesAssistModule.buildWaveForecastLine(new int[]{2}, new int[]{4}, new int[]{5}),
                ZombiesAssistModule.buildNextWaveAlert(new int[]{2}, new int[0], new int[0], 1, 6),
                ZombiesRoundData.roundTypeHint(70));
        for (String line : all) {
            if (line == null) continue;
            assertFalse(line.contains("Alien Arcadium"), line);
            assertFalse(line.contains("Dead / quit"), line);
            assertFalse(line.contains("Down players"), line);
        }
    }

    /** T1-TR8（rubric 锚点）：块1+块2 去重色码 ≥8 种，含 6/7/8/b/2/d/5/c。 */
    @Test
    void blockLinesCarryRichForgeColorCoding() {
        List<String> lines = List.of(
                ZombiesAssistModule.buildRoundLine(90, 0, 6, ZombiesRoundData.roundMobs(90)),
                ZombiesAssistModule.buildRoundLine(44, 7, 7, null),
                "\u00a77\u4e0b\u6ce2\u5012\u8ba1\u65f6 \u00a7f7.0s",
                "\u00a77\u4e0b\u56de\u5408 \u00a7fult/alt squat",
                ZombiesAssistModule.buildClearMobLine(299_999L),
                ZombiesAssistModule.buildWaveForecastLine(new int[]{2, 3}, new int[]{4}, new int[]{5, 6}),
                ZombiesRoundData.roundTypeHint(70),
                ZombiesAssistModule.buildNextWaveAlert(new int[]{2}, new int[0], new int[0], 1, 6));
        Set<String> codes = lines.stream()
                .filter(java.util.Objects::nonNull)
                .flatMap(line -> {
                    java.util.stream.Stream.Builder<String> builder = java.util.stream.Stream.builder();
                    Matcher m = CODE.matcher(line);
                    while (m.find()) builder.add(m.group(1));
                    return builder.build();
                })
                .collect(Collectors.toSet());
        assertTrue(codes.size() >= 8, "codes=" + codes);
        for (String required : new String[]{"6", "7", "8", "b", "2", "d", "5", "c", "f"}) {
            assertTrue(codes.contains(required), "missing " + required + " in " + codes);
        }
    }

    /** directionTo：8 方位纯函数（Forge 语义，MC yaw=0 朝 +Z）。 */
    @Test
    void directionArrowsFollowForgeBearingMath() {
        assertEquals("\u2191", ZombiesAssistModule.directionTo(0, 10, 0));    // 正前（+Z）
        assertEquals("\u2193", ZombiesAssistModule.directionTo(0, -10, 0));   // 正后（-Z）
        assertEquals("\u2192", ZombiesAssistModule.directionTo(-10, 0, 0));   // 右（-X，面 +Z 时右为西）
        assertEquals("\u2190", ZombiesAssistModule.directionTo(10, 0, 0));    // 左（+X）
    }

    /** join：波次逗号串。 */
    @Test
    void joinProducesCommaList() {
        assertEquals("", ZombiesRoundData.join(new int[0]));
        assertEquals("2", ZombiesRoundData.join(new int[]{2}));
        assertEquals("2,3,5", ZombiesRoundData.join(new int[]{2, 3, 5}));
    }
}
