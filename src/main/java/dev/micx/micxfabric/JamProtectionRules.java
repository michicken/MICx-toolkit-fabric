package dev.micx.micxfabric;

import java.util.Arrays;

/** Minecraft-free rules shared by the client protection state machine and tests. */
final class JamProtectionRules {
    static final long VERY_LOW_STABLE_MS = 150L;
    static final long PROTECT_COOLDOWN_MS = 2_500L;
    static final long STUCK_PAUSE_MS = 200L;
    static final long DOWN_GUN_WINDOW_MS = 100L;
    static final long DOWN_SLOT_WAIT_MS = 150L;
    static final long DOWN_COOLDOWN_MS = 1_000L;
    static final int[] SPECIAL_ROUNDS = {53, 54, 55, 58, 65, 69, 70};

    private JamProtectionRules() {
    }

    static boolean isSpecialRound(int round) {
        if (round <= 0) return false;
        if (Arrays.binarySearch(SPECIAL_ROUNDS, round) >= 0) return true;
        if (round > 80) {
            int last = round % 10;
            return last == 0 || last == 5 || last == 9;
        }
        return false;
    }

    static int[] findRecoveredGunSlots(int[] beforeCounts, int[] currentCounts, boolean[] weaponSlots) {
        if (beforeCounts == null || currentCounts == null || weaponSlots == null) return new int[0];
        int limit = Math.min(9, Math.min(beforeCounts.length, Math.min(currentCounts.length, weaponSlots.length)));
        int[] result = new int[Math.max(0, limit - 1)];
        int count = 0;
        for (int slot = 1; slot < limit; slot++) {
            if (weaponSlots[slot] && beforeCounts[slot] > 1 && currentCounts[slot] == 1) {
                result[count++] = slot;
            }
        }
        return Arrays.copyOf(result, count);
    }

    static boolean allEmpty(int[] counts) {
        if (counts == null || counts.length < 9) return false;
        for (int i = 0; i < 9; i++) if (counts[i] > 0) return false;
        return true;
    }
}
