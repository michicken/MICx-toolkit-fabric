package dev.micx.micxfabric;

import java.util.HashMap;
import java.util.Map;

/** Forge AAData round text and fixed wave tables used by the automatic /pc announcement. */
public final class ZombiesRoundData {
    private static final int[] EMPTY = new int[0];
    private static final long MOB_CLEAR_MS = 300_000L;
    private static final Map<Integer, int[]> TOO_WAVES = new HashMap<>();
    private static final Map<Integer, int[]> GIANT_WAVES = new HashMap<>();
    private static final Map<Integer, int[]> TOO_GIANT_WAVES = new HashMap<>();

    static {
        TOO_WAVES.put(40, new int[]{5});
        TOO_WAVES.put(45, new int[]{3, 4});
        TOO_WAVES.put(46, new int[]{4});
        TOO_WAVES.put(48, new int[]{4});
        TOO_WAVES.put(54, new int[]{5});
        TOO_WAVES.put(55, new int[]{6});
        TOO_WAVES.put(58, new int[]{5});
        TOO_WAVES.put(59, new int[]{1, 2, 3, 4, 5, 6});
        TOO_WAVES.put(60, new int[]{3, 4});
        TOO_WAVES.put(64, new int[]{5, 6});
        TOO_WAVES.put(67, new int[]{6});
        TOO_WAVES.put(68, new int[]{5, 6});
        TOO_WAVES.put(69, new int[]{5, 6});
        TOO_WAVES.put(70, new int[]{2, 3});
        TOO_WAVES.put(74, new int[]{4, 5, 6});
        TOO_WAVES.put(77, new int[]{6});
        TOO_WAVES.put(78, new int[]{5, 6});
        TOO_WAVES.put(79, new int[]{5, 6});
        TOO_WAVES.put(80, new int[]{2, 3});
        TOO_WAVES.put(84, new int[]{4, 5, 6});
        TOO_WAVES.put(87, new int[]{6});
        TOO_WAVES.put(88, new int[]{5, 6});
        TOO_WAVES.put(89, new int[]{5, 6});
        TOO_WAVES.put(90, new int[]{2, 3});
        TOO_WAVES.put(94, new int[]{4, 5, 6});
        TOO_WAVES.put(97, new int[]{6});
        TOO_WAVES.put(98, new int[]{5, 6});
        TOO_WAVES.put(99, new int[]{5, 6});
        TOO_WAVES.put(100, new int[]{2, 3});

        GIANT_WAVES.put(15, new int[]{6});
        GIANT_WAVES.put(20, new int[]{3, 5});
        GIANT_WAVES.put(22, new int[]{4, 6});
        GIANT_WAVES.put(24, new int[]{2, 4, 6});
        GIANT_WAVES.put(30, new int[]{1, 2, 3});
        GIANT_WAVES.put(36, new int[]{2, 3});
        GIANT_WAVES.put(37, new int[]{2, 3});
        GIANT_WAVES.put(38, new int[]{2, 3});
        GIANT_WAVES.put(39, new int[]{2, 3});
        GIANT_WAVES.put(40, new int[]{2, 3});
        GIANT_WAVES.put(41, new int[]{2, 3});
        GIANT_WAVES.put(42, new int[]{1, 2, 3});
        GIANT_WAVES.put(43, new int[]{2, 4, 6});
        GIANT_WAVES.put(44, new int[]{1, 2, 3});
        GIANT_WAVES.put(45, new int[]{2});
        GIANT_WAVES.put(47, new int[]{3});
        GIANT_WAVES.put(50, new int[]{2, 4});
        GIANT_WAVES.put(51, new int[]{2, 4});
        GIANT_WAVES.put(52, new int[]{2, 4});
        GIANT_WAVES.put(53, new int[]{2, 4});
        GIANT_WAVES.put(54, new int[]{4});
        GIANT_WAVES.put(55, new int[]{1, 2, 3, 4});
        GIANT_WAVES.put(58, new int[]{4});
        GIANT_WAVES.put(65, new int[]{4, 5, 6});
        GIANT_WAVES.put(75, new int[]{4, 5, 6});
        GIANT_WAVES.put(85, new int[]{4, 5, 6});
        GIANT_WAVES.put(95, new int[]{4, 5, 6});

        TOO_GIANT_WAVES.put(54, new int[]{2});
        TOO_GIANT_WAVES.put(55, new int[]{5});
        TOO_GIANT_WAVES.put(58, new int[]{2});
        TOO_GIANT_WAVES.put(70, new int[]{4, 5, 6});
        TOO_GIANT_WAVES.put(80, new int[]{4, 5, 6});
        TOO_GIANT_WAVES.put(90, new int[]{4, 5, 6});
        TOO_GIANT_WAVES.put(100, new int[]{4, 5, 6});
    }

    static String roundMobs(int round) {
        round = normalize(round);
        return switch (round) {
            case 1, 2, 3, 4 -> "zombies + skeletons";
            case 5, 6, 7, 8 -> "zombies + pigz";
            case 9 -> "13 zombies";
            case 10 -> "zombies + 2 slime";
            case 11 -> "zombies + pigz";
            case 12 -> "zombies + worm";
            case 13 -> "zombies + 6 pigz";
            case 14 -> "24 zombies (grunts)";
            case 15 -> "zombies + slime + 1 GIANT";
            case 16 -> "zombies + pigz + slime";
            case 17 -> "zombies + 6 pigz";
            case 18 -> "SLIME grow + 6 ghast";
            case 19 -> "32 zombies + squid + ghast";
            case 20 -> "GIANT + golem + clown";
            case 21 -> "zombies + 6 ghast (sentinel)";
            case 22 -> "zombies + GIANT";
            case 23 -> "SLIME grow (22)";
            case 24 -> "zombies + ghast";
            case 25 -> "25 slime (reward)";
            case 26 -> "44 BLOB feed";
            case 27 -> "zombies + squid";
            case 28 -> "30 zombies + ghast";
            case 29 -> "21 BLOB + clown";
            case 30 -> "2 GIANT + ghast";
            case 31 -> "11 skele + blob + golem";
            case 32 -> "zombies + pigz + golem";
            case 33 -> "blob + ghast + golem";
            case 34 -> "29 BLOB + 18 pigz + golem";
            case 35 -> "35 BLOB (mega reward)";
            case 36 -> "35 zombies + 2 GIANT + clown";
            case 37 -> "2 GIANT + ghast";
            case 38 -> "2 GIANT + pigz";
            case 39 -> "66 BLOB + 3 GIANT !";
            case 40 -> "TOO! + 2 GIANT + ghast";
            case 41 -> "squid + 6 golem + ghast + GIANT";
            case 42 -> "36 zombies + 6 golem + 2 GIANT";
            case 43 -> "44 BLOB + 40 slime + 6 GIANT !";
            case 44 -> "GIANT w1, clear it first";
            case 45 -> "TOO + GIANT";
            case 46 -> "golem + TOO (free gold)";
            case 47 -> "blob + GIANT";
            case 48 -> "creeper + TOO";
            case 49 -> "clown pack";
            case 50 -> "creeper + golem + GIANT";
            case 51 -> "free gold (door) + GIANT";
            case 52 -> "slime + GIANT";
            case 53 -> "clown + GIANT";
            case 54 -> "4 rainbow GIANT + 2 elder + clown + golem + TOO";
            case 55 -> "blob + golem + GIANT + TOO";
            case 56, 57 -> "golem + mega slime (reward)";
            case 58 -> "clown + TOO + GIANT (mini r70)";
            case 59 -> "13x TOO !!";
            case 60, 61, 62, 63 -> "ult/alt squat";
            case 64 -> "blaze + TOO";
            case 65 -> "ult/alt + GIANT";
            case 66 -> "ult/alt";
            case 67, 68, 69 -> "ult/alt + TOO";
            case 70 -> "LS: 6 GIANT + clown + golem + TOO";
            default -> null;
        };
    }

    static String ecoAdvice(int round) {
        round = normalize(round);
        String advice = switch (round) {
            case 18, 23 -> "cc, grow, hit on arrival";
            case 25, 35 -> "mega slime round";
            case 26 -> "cc / old spot / perk";
            case 29, 31, 33, 34 -> "perk corner (slime)";
            case 36 -> "rc, many mobs, careful";
            case 37 -> "giant this round too, stay outside";
            case 38 -> "easier than r36";
            case 39 -> "cc, BLOCK until giant";
            case 40 -> "rc, TOO debut, focus or kite it";
            case 42 -> "many mobs + giant w1, careful";
            case 43 -> "cc, BLOCK, giant on slime grow";
            case 44 -> "giant w1, clear giant first";
            case 45 -> "ult, watch TOO";
            case 46 -> "free gold, squat cc, 1 lures golem";
            case 47 -> "like r39, no golem before giant";
            case 48 -> "cc, watch TOO";
            case 49 -> "1 speed kites mid, 3 squat alt";
            case 50 -> "ult/alt, 3rd+LR";
            case 51 -> "door, free gold";
            case 52 -> "like r39, full diamond round";
            case 53, 54 -> "ult/alt, 3rd";
            case 55 -> "ult/alt, 3rd if not swapped";
            case 56, 57 -> "last free gold, roll chest";
            case 58 -> "ult/alt (r70 lite)";
            case 59 -> "13 TOO! spread to 1-high spots";
            case 60 -> "ult, optional agro";
            case 61 -> "ult if swapped, else cc + roll";
            case 62, 63, 66 -> "cc";
            case 64 -> "ult, blaze debut + TOO, risky";
            case 65, 67 -> "alt, need dps";
            case 68, 69 -> "ult, TOO";
            case 70 -> "LS! 3+1, block + LR cycle";
            default -> null;
        };
        return advice == null ? null : "ECO " + advice;
    }

    /**
     * Forge AAData.roundTypeHint 逐字符对齐（七类 § 色码）。
     * 优先级：LS > SLIME_BOSS > SLIME_GIANT > SLIME > MONEY > TOO > ULT_SQUAT。
     * 注意 SLIME_BOSS(25/35) 只在 Fabric 映射 25/35（Forge 的 56/57 走 MONEY）。
     */
    static String roundTypeHint(int round) {
        if (isLsRound(round)) return "\u00a7c\u00a7lLS ROUND \u00a7r\u00a77block + LR, 3+1";
        int n = normalize(round);
        if (n == 25 || n == 35) return "\u00a76\u00a7lMEGA SLIME \u00a7r\u00a77reward round, free gold";
        if (n == 39 || n == 43 || n == 47 || n == 52) {
            return "\u00a7e\u00a7lSLIME+GIANT \u00a7r\u00a77block until giant";
        }
        if (n == 18 || n == 23 || n == 29 || n == 31 || n == 33 || n == 34) {
            return "\u00a7a\u00a7lSLIME \u00a7r\u00a77grow! no insta kill";
        }
        if (n == 46 || n == 51 || n == 56 || n == 57) return "\u00a76FREE GOLD \u00a77squat cc";
        if (n == 40 || n == 44 || n == 59) return "\u00a7d\u00a7lTOO \u00a7r\u00a77watch for The Old One";
        if (n == 45 || n == 48 || n == 50 || n == 53 || n == 54 || n == 55
                || n == 58 || n == 60) {
            return "\u00a7bULT/ALT \u00a77squat + 3rd+LR";
        }
        return null;
    }

    /** 波次数组转逗号串（Forge join 语义）。 */
    static String join(int[] waves) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < waves.length; i++) {
            if (i > 0) b.append(',');
            b.append(waves[i]);
        }
        return b.toString();
    }

    static boolean isLsRound(int round) {
        return normalize(round) == 70;
    }

    static int[] tooWaves(int round) {
        return TOO_WAVES.getOrDefault(round, EMPTY);
    }

    static int[] giantWaves(int round) {
        return GIANT_WAVES.getOrDefault(round, EMPTY);
    }

    static int[] tooGiantWaves(int round) {
        return TOO_GIANT_WAVES.getOrDefault(round, EMPTY);
    }

    static long mobClearRemainingMs(int round, long elapsedMs) {
        int[] times = waveTimes(round);
        if (times.length == 0 || elapsedMs < times[times.length - 1] * 1_000L) return -1L;
        long remaining = times[times.length - 1] * 1_000L + MOB_CLEAR_MS - elapsedMs;
        return remaining > 0L && remaining <= MOB_CLEAR_MS ? remaining : -1L;
    }

    static int weakPuProb(String kind, int round) {
        if ("dg".equals(kind)) {
            return switch (round) {
                case 1, 2, 3 -> 35;
                case 10, 11, 12 -> 45;
                case 13 -> 35;
                case 16 -> 30;
                case 23 -> 40;
                case 24 -> 50;
                case 28 -> 55;
                case 29 -> 40;
                case 30 -> 55;
                case 34 -> 40;
                case 37, 39, 40 -> 55;
                case 41, 50, 59, 60 -> 45;
                default -> round >= 61 && (round % 10 == 0 || round % 10 == 6
                        || round % 10 == 7 || round % 10 == 8) ? 35 : 0;
            };
        }
        if ("carp".equals(kind)) {
            return switch (round) {
                case 1, 2 -> 30;
                case 5 -> 40;
                case 11, 12 -> 30;
                case 39 -> 40;
                case 41, 43 -> 45;
                case 46, 48, 59 -> 50;
                case 101, 102, 103, 104, 105 -> 60;
                default -> round >= 61 && round % 10 == 1 ? 30 : 0;
            };
        }
        if ("bg".equals(kind)) {
            return switch (round) {
                case 9, 10, 11 -> 25;
                case 12, 15 -> 30;
                default -> 0;
            };
        }
        return 0;
    }

    static String dgForecast(int round) {
        return weakForecast("DG", "dg", round, 45, 105, 6);
    }

    static String carpForecast(int round) {
        return round >= 55 ? null : weakForecast("CARP", "carp", round, 40, 54, 6);
    }

    static String bgForecast(int round) {
        return weakForecast("BG", "bg", round, 25, 105, 4);
    }

    private static String weakForecast(String label, String kind, int round,
                                       int minimum, int maxRound, int limit) {
        StringBuilder result = new StringBuilder();
        int count = 0;
        for (int candidate = Math.max(1, round); candidate <= maxRound && count < limit; candidate++) {
            if (weakPuProb(kind, candidate) < minimum) continue;
            if (result.length() > 0) result.append(',');
            result.append('R').append(candidate);
            count++;
        }
        return count == 0 ? null : label + " advantage " + result;
    }

    /** Returns the complete Forge wave schedule without applying text-table normalization. */
    public static int[] waveTimes(int round) {
        return ZombiesWaveSchedule.waveTimes(round);
    }

    static int waveAt(int round, long elapsedMs) {
        return ZombiesWaveSchedule.waveAt(round, elapsedMs);
    }

    /** Returns the LS wave schedule shared by rounds 70/80/90/100. */
    static int[] lsWaveTimes(int round) {
        return isLsRound(round) ? ZombiesWaveSchedule.waveTimes(70) : new int[0];
    }

    /** Returns milliseconds until the next giant wave for the four confirmed slime+giant rounds. */
    static long blockCountdownMs(int round, long elapsedMs) {
        round = normalize(round);
        int[] waveTimes;
        int[] giantWaves;
        switch (round) {
            case 39 -> {
                waveTimes = ZombiesWaveSchedule.waveTimes(39);
                giantWaves = new int[]{2, 3};
            }
            case 47 -> {
                waveTimes = ZombiesWaveSchedule.waveTimes(47);
                giantWaves = new int[]{3};
            }
            case 43 -> {
                waveTimes = ZombiesWaveSchedule.waveTimes(43);
                giantWaves = new int[]{2, 4, 6};
            }
            case 52 -> {
                waveTimes = ZombiesWaveSchedule.waveTimes(52);
                giantWaves = new int[]{2, 4};
            }
            default -> {
                return -1L;
            }
        }
        for (int wave : giantWaves) {
            int index = wave - 1;
            if (index < 0 || index >= waveTimes.length) continue;
            long remaining = waveTimes[index] * 1_000L - elapsedMs;
            if (remaining >= 0L && remaining <= 3_000L) return remaining;
        }
        return -1L;
    }

    static String waveTempo(int round, long elapsedMs) {
        return ZombiesWaveSchedule.fallbackLine(round, elapsedMs);
    }

    private static int normalize(int round) {
        if (round >= 71 && round <= 105) {
            int modulo = round % 10;
            return modulo == 0 ? 70 : 60 + modulo;
        }
        return round;
    }
}
