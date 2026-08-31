package dev.micx.micxfabric;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Power-up round pattern data ported verbatim from SST 2.1.1 PowerupDetect (map-adaptive latch). */
final class ZombiesPuRounds {
    // ---- Max Ammo ----
    static final Integer[] R2_MAX_DE = {2, 8, 12, 16, 21, 26};
    static final Integer[] R2_MAX_BB = {2, 5, 8, 12, 16, 21, 26};
    static final Integer[] R3_MAX_DEBB = {3, 6, 9, 13, 17, 22, 27};
    static final Integer[] R2_MAX_TL = {2, 8, 12, 16, 21, 26, 31, 36};
    static final Integer[] R3_MAX_TL = {3, 6, 9, 13, 17, 22, 27, 32, 37};
    static final Integer[] R2_MAX_PR = {2, 5, 8, 12, 16, 21, 26};
    static final Integer[] R3_MAX_PR = {3, 6, 9, 13, 17, 22, 27};
    static final Integer[] R2_MAX_AA = {2, 5, 8, 12, 16, 21, 26, 31, 36, 41, 46, 51, 61, 66, 71, 76, 81, 86, 91, 96};
    static final Integer[] R3_MAX_AA = {3, 6, 9, 13, 17, 22, 27, 32, 37, 42, 47, 52, 62, 67, 72, 77, 82, 87, 92, 97};
    // ---- Insta Kill ----
    static final Integer[] R2_INS_DE = {2, 8, 11, 14, 17, 23};
    static final Integer[] R2_INS_BB = {2, 5, 8, 11, 14, 17, 23};
    static final Integer[] R3_INS_DEBB = {3, 6, 9, 12, 18, 21, 24};
    static final Integer[] R2_INS_TL = {2, 8, 11, 14, 17, 23};
    static final Integer[] R3_INS_TL = {3, 6, 9, 12, 18, 21, 24};
    static final Integer[] R2_INS_PR = {2, 5, 8, 11, 14, 17, 23};
    static final Integer[] R3_INS_PR = {3, 6, 9, 12, 15, 18, 21, 24};
    static final Integer[] R2_INS_AA = {2, 5, 8, 11, 14, 17, 20, 23};
    static final Integer[] R3_INS_AA = {3, 6, 9, 12, 15, 18, 21};
    // ---- Shopping Spree (AA only) ----
    static final Integer[] R5_SS_AA = {5, 15, 45, 55, 65, 75, 85, 95, 105};
    static final Integer[] R6_SS_AA = {6, 16, 26, 36, 46, 66, 76, 86, 96};
    static final Integer[] R7_SS_AA = {7, 17, 27, 37, 47, 67, 77, 87, 97};

    private List<Integer> insRounds = new ArrayList<>();
    private List<Integer> maxRounds = new ArrayList<>();
    private List<Integer> ssRounds = new ArrayList<>();

    void reset() {
        insRounds = new ArrayList<>();
        maxRounds = new ArrayList<>();
        ssRounds = new ArrayList<>();
    }

    boolean known() {
        return !insRounds.isEmpty() || !maxRounds.isEmpty() || !ssRounds.isEmpty();
    }

    List<Integer> insRounds() { return Collections.unmodifiableList(insRounds); }
    List<Integer> maxRounds() { return Collections.unmodifiableList(maxRounds); }
    List<Integer> ssRounds() { return Collections.unmodifiableList(ssRounds); }

    int amountFor(int round) {
        int c = 0;
        if (insRounds.contains(round)) c++;
        if (maxRounds.contains(round)) c++;
        if (ssRounds.contains(round)) c++;
        return c;
    }

    /** Latch Insta variant from a chat-activated INSTA_KILL at round with elapsedMs since round start. */
    void latchInsta(int round, long elapsedMs, WaveTable.ZbMap map) {
        if (!insRounds.isEmpty()) return;
        int variant = chooseInsVariant(round, elapsedMs, map);
        if (variant == 0) return;
        insRounds = resolveIns(map, variant);
    }

    void latchMax(int round, long elapsedMs, WaveTable.ZbMap map) {
        if (!maxRounds.isEmpty()) return;
        int variant = chooseMaxVariant(round, elapsedMs, map);
        if (variant == 0) return;
        maxRounds = resolveMax(map, variant);
    }

    void latchSs(int round, long elapsedMs) {
        if (!ssRounds.isEmpty()) return;
        int variant = chooseSsVariant(round, elapsedMs);
        if (variant == 0) return;
        ssRounds = resolveSs(variant);
    }

    // ---- variant choosers (SST PowerupDetect.onChatReceived latch logic) ----
    private static int chooseInsVariant(int round, long elapsedMs, WaveTable.ZbMap map) {
        // Non-AA maps use same branching: 2→r2, 3+early(≤500ms)→r2, 3→r3, 4→r3
        // AA uses R2_INS_AA / R3_INS_AA likewise.
        if (round == 2) return 2;
        if (round == 3 && elapsedMs <= 500) return 2;
        if (round == 3) return 3;
        if (round == 4) return 3;
        // For later rounds or unknown, try to infer from data contains
        // (fallback for armor-stand path with 1000ms threshold)
        return 0;
    }

    private static int chooseMaxVariant(int round, long elapsedMs, WaveTable.ZbMap map) {
        if (round == 2) return 2;
        if (round == 3 && elapsedMs <= 500) return 2;
        if (round == 3) return 3;
        if (round == 4) return 3;
        return 0;
    }

    private static int chooseSsVariant(int round, long elapsedMs) {
        if (round == 5) return 5;
        if (round == 6 && elapsedMs <= 500) return 5;
        if (round == 6) return 6;
        if (round == 7 && elapsedMs <= 500) return 6;
        if (round == 7) return 7;
        if (round == 8) return 7;
        return 0;
    }

    // Armor-stand early detection (SST detectArmorstand path, threshold 1000ms)
    void latchFromArmorStand(String kind, int round, long elapsedMs, WaveTable.ZbMap map) {
        String k = kind == null ? "" : kind.toLowerCase(java.util.Locale.ROOT);
        boolean isMax = k.contains("max");
        boolean isInsta = k.contains("insta");
        boolean isSs = k.contains("shopping") || k.contains("ss");
        if (isMax && maxRounds.isEmpty()) {
            Integer variant = chooseArmorStandVariant(round, elapsedMs, true, false);
            if (variant != null) maxRounds = resolveMax(map, variant);
        } else if (isInsta && insRounds.isEmpty()) {
            Integer variant = chooseArmorStandVariant(round, elapsedMs, false, true);
            if (variant != null) insRounds = resolveIns(map, variant);
        } else if (isSs && ssRounds.isEmpty()) {
            Integer variant = chooseArmorStandSsVariant(round, elapsedMs);
            if (variant != null) ssRounds = resolveSs(variant);
        }
    }

    private static Integer chooseArmorStandVariant(int round, long elapsedMs, boolean isMax, boolean isInsta) {
        // SST ArmorStand path threshold 1000ms
        List<Integer> r2 = List.of(R2_MAX_AA); // only AA supports early armor stand pre-round inference
        // Simplified: reuse chat latch for non-AA
        if (round == 2) return 2;
        // Check r2 contains vs r3
        // We need map-agnostic check: use AA lists as superset
        boolean r2Contains = containsAny(R2_MAX_AA, round) || containsAny(R2_INS_AA, round);
        boolean r3Contains = containsAny(R3_MAX_AA, round) || containsAny(R3_INS_AA, round);
        if (r2Contains && !r3Contains) return 2;
        if (!r2Contains && r3Contains && elapsedMs > 1000) return 3;
        if (r2Contains && r3Contains && elapsedMs <= 1000) return 2;
        if (r3Contains) return 3;
        // Fallback: previous round early
        if (containsAny(R3_MAX_AA, round - 1) && elapsedMs <= 1000) return 3;
        if (containsAny(R3_INS_AA, round - 1) && elapsedMs <= 1000) return 3;
        return null;
    }

    private static Integer chooseArmorStandSsVariant(int round, long elapsedMs) {
        boolean r5 = Arrays.asList(R5_SS_AA).contains(round);
        boolean r6 = Arrays.asList(R6_SS_AA).contains(round);
        boolean r7 = Arrays.asList(R7_SS_AA).contains(round);
        if (r5 && !r6 && !r7) return 5;
        if (r6 && elapsedMs > 1000) return 6;
        if (r6 && elapsedMs <= 1000 && r5) return 5;
        if (r6) return 6;
        if (r7 && elapsedMs > 1000) return 7;
        if (r7 && elapsedMs <= 1000) return 6;
        if (Arrays.asList(R7_SS_AA).contains(round - 1) && elapsedMs <= 1000) return 7;
        return null;
    }

    private static boolean containsAny(Integer[] arr, int v) {
        for (int x : arr) if (x == v) return true;
        return false;
    }

    private static List<Integer> resolveIns(WaveTable.ZbMap map, int variant) {
        if (map == null) map = WaveTable.ZbMap.DEAD_END;
        return switch (map) {
            case DEAD_END -> Arrays.asList(variant == 2 ? R2_INS_DE : R3_INS_DEBB);
            case BAD_BLOOD -> Arrays.asList(variant == 2 ? R2_INS_BB : R3_INS_DEBB);
            case THE_LAB -> Arrays.asList(variant == 2 ? R2_INS_TL : R3_INS_TL);
            case PRISON -> Arrays.asList(variant == 2 ? R2_INS_PR : R3_INS_PR);
            default -> Arrays.asList(new Integer[0]); // AA case handled separately
        };
    }

    static List<Integer> resolveInsForMap(WaveTable.ZbMap map, int variant) {
        if (map == WaveTable.ZbMap.DEAD_END || map == WaveTable.ZbMap.BAD_BLOOD
                || map == WaveTable.ZbMap.THE_LAB || map == WaveTable.ZbMap.PRISON) {
            return resolveIns(map, variant);
        }
        // Alien Arcadium
        return Arrays.asList(variant == 2 ? R2_INS_AA : R3_INS_AA);
    }

    private static List<Integer> resolveMax(WaveTable.ZbMap map, int variant) {
        if (map == null) map = WaveTable.ZbMap.DEAD_END;
        return switch (map) {
            case DEAD_END -> Arrays.asList(variant == 2 ? R2_MAX_DE : R3_MAX_DEBB);
            case BAD_BLOOD -> Arrays.asList(variant == 2 ? R2_MAX_BB : R3_MAX_DEBB);
            case THE_LAB -> Arrays.asList(variant == 2 ? R2_MAX_TL : R3_MAX_TL);
            case PRISON -> Arrays.asList(variant == 2 ? R2_MAX_PR : R3_MAX_PR);
            default -> Arrays.asList(new Integer[0]);
        };
    }

    static List<Integer> resolveMaxForMap(WaveTable.ZbMap map, int variant) {
        if (map == WaveTable.ZbMap.DEAD_END || map == WaveTable.ZbMap.BAD_BLOOD
                || map == WaveTable.ZbMap.THE_LAB || map == WaveTable.ZbMap.PRISON) {
            return resolveMax(map, variant);
        }
        return Arrays.asList(variant == 2 ? R2_MAX_AA : R3_MAX_AA);
    }

    private static List<Integer> resolveSs(int variant) {
        return switch (variant) {
            case 5 -> Arrays.asList(R5_SS_AA);
            case 6 -> Arrays.asList(R6_SS_AA);
            case 7 -> Arrays.asList(R7_SS_AA);
            default -> List.of();
        };
    }
}
