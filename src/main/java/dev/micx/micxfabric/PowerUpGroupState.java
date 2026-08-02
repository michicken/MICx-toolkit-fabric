package dev.micx.micxfabric;

import java.util.HashMap;
import java.util.Map;

/** Forge-compatible Max/INS/SS power-up group locks based on observed drop rounds. */
final class PowerUpGroupState {
    private static final PowerUpGroupState INSTANCE = new PowerUpGroupState();
    private static final int[] MAX_R2 = {2, 5, 8, 12, 16, 21, 26, 31, 36, 41, 46, 51, 61, 66, 71, 76, 81, 86, 91, 96};
    private static final int[] MAX_R3 = {3, 6, 9, 13, 17, 22, 27, 32, 37, 42, 47, 52, 62, 67, 72, 77, 82, 87, 92, 97};
    private static final int[] INS_R2 = {2, 5, 8, 11, 14, 17, 20, 23};
    private static final int[] INS_R3 = {3, 6, 9, 12, 15, 18, 21};
    private static final int[] SS_R5 = {5, 15, 45, 55, 65, 75, 85, 95, 105};
    private static final int[] SS_R6 = {6, 16, 26, 36, 46, 66, 76, 86, 96};
    private static final int[] SS_R7 = {7, 17, 27, 37, 47, 67, 77, 87, 97};

    private final Map<String, Integer> groups = new HashMap<>();

    private PowerUpGroupState() {
    }

    static PowerUpGroupState instance() {
        return INSTANCE;
    }

    synchronized void clear() {
        groups.clear();
    }

    synchronized void lock(String kind, int round) {
        if (round <= 0) return;
        int group = switch (kind) {
            case "max" -> groupOf(round, new int[][]{MAX_R2, MAX_R3}, new int[]{2, 3});
            case "insta" -> groupOf(round, new int[][]{INS_R2, INS_R3}, new int[]{2, 3});
            case "shopping" -> groupOf(round, new int[][]{SS_R5, SS_R6, SS_R7}, new int[]{5, 6, 7});
            default -> 0;
        };
        if (group != 0) groups.put(kind, group);
    }

    synchronized int group(String kind) {
        return groups.getOrDefault(kind, 0);
    }

    synchronized String forecast(String kind, int round) {
        if (round <= 0) return null;
        int locked = group(kind);
        int next;
        boolean uncertain = locked == 0;
        if ("max".equals(kind)) {
            next = locked == 2 ? nextAfter(MAX_R2, round) : locked == 3 ? nextAfter(MAX_R3, round)
                    : nearest(round, MAX_R2, MAX_R3);
        } else if ("insta".equals(kind)) {
            next = locked == 2 ? nextAfter(INS_R2, round) : locked == 3 ? nextAfter(INS_R3, round)
                    : nearest(round, INS_R2, INS_R3);
        } else if ("shopping".equals(kind)) {
            next = locked == 5 ? nextAfter(SS_R5, round) : locked == 6 ? nextAfter(SS_R6, round)
                    : locked == 7 ? nextAfter(SS_R7, round) : nearest(round, SS_R5, SS_R6, SS_R7);
        } else {
            return null;
        }
        if (next < 0) return null;
        return (next == round ? "NOW" : "R" + next) + (uncertain ? "?" : "");
    }

    private static int nearest(int round, int[]... sets) {
        int result = -1;
        for (int[] set : sets) {
            int candidate = nextAfter(set, round);
            if (candidate >= 0 && (result < 0 || candidate < result)) result = candidate;
        }
        return result;
    }

    private static int nextAfter(int[] set, int round) {
        for (int candidate : set) {
            if (candidate >= round) return candidate;
        }
        return -1;
    }

    private static int groupOf(int round, int[][] sets, int[] labels) {
        for (int i = 0; i < sets.length; i++) {
            for (int candidate : sets[i]) {
                if (candidate == round) return labels[i];
            }
        }
        return 0;
    }
}
