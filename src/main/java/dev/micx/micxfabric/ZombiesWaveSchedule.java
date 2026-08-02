package dev.micx.micxfabric;

/** Complete Alien Arcadium wave schedule copied from Forge AAData.WAVES. */
final class ZombiesWaveSchedule {
    private static final int[] NORMAL = {10, 14, 18, 22, 26, 30};
    private static final int[][] WAVES = buildWaves();

    private ZombiesWaveSchedule() {
    }

    private static int[][] buildWaves() {
        int[][] waves = new int[105][];
        waves[0] = new int[]{10, 13, 16, 19};
        waves[1] = new int[]{10, 14, 18, 22};
        waves[2] = new int[]{10, 13, 16, 19};
        waves[3] = new int[]{10, 14, 17, 21, 25, 28};
        waves[4] = NORMAL;
        waves[5] = new int[]{10, 14, 19, 23, 28, 32};
        waves[6] = new int[]{10, 15, 19, 23, 27, 31};
        waves[7] = new int[]{10, 15, 20, 25, 30, 35};
        waves[8] = new int[]{10, 14, 19, 23, 28, 32};
        waves[9] = new int[]{10, 16, 22, 27, 33, 38};
        waves[10] = new int[]{10, 16, 21, 27, 32, 38};
        waves[11] = new int[]{10, 16, 22, 28, 34, 40};
        waves[12] = new int[]{10, 16, 22, 28, 34, 40};
        waves[13] = new int[]{10, 16, 21, 26, 31, 36};
        waves[14] = new int[]{10, 17, 24, 31, 38, 46};
        waves[15] = new int[]{10, 16, 22, 27, 33, 38};
        waves[16] = new int[]{10, 14, 19, 23, 28, 32};
        waves[17] = new int[]{10, 14, 19, 23, 28, 32};
        waves[18] = NORMAL;
        waves[19] = new int[]{10, 15, 21, 26, 31, 36};
        waves[20] = new int[]{10, 14, 19, 23, 28, 32};
        waves[21] = new int[]{10, 14, 19, 23, 28, 34};
        waves[22] = NORMAL;
        waves[23] = new int[]{10, 14, 19, 23, 28, 32};
        waves[24] = new int[]{10};
        waves[25] = new int[]{10, 23, 36};
        waves[26] = new int[]{10, 22, 34};
        waves[27] = new int[]{10, 20, 30};
        waves[28] = new int[]{10, 24, 38};
        waves[29] = new int[]{10, 22, 34};
        waves[30] = new int[]{10, 22, 34};
        waves[31] = new int[]{10, 21, 32};
        waves[32] = new int[]{10, 22, 34};
        waves[33] = new int[]{10, 22, 34};
        waves[34] = new int[]{10};
        waves[35] = new int[]{10, 22, 34};
        waves[36] = new int[]{10, 20, 31};
        waves[37] = new int[]{10, 22, 34};
        waves[38] = new int[]{10, 22, 34};
        waves[39] = new int[]{10, 22, 34, 37, 45};
        waves[40] = new int[]{10, 21, 32};
        waves[41] = new int[]{10, 22, 34};
        waves[42] = new int[]{10, 13, 22, 25, 34, 37};
        waves[43] = new int[]{10, 22, 34};
        waves[44] = new int[]{10, 22, 34, 35};
        waves[45] = new int[]{10, 21, 32, 35};
        waves[46] = new int[]{10, 20, 30};
        waves[47] = new int[]{10, 20, 30, 33};
        waves[48] = new int[]{10, 21, 32};
        waves[49] = new int[]{10, 22, 34, 37};
        waves[50] = new int[]{10, 20, 30, 33};
        waves[51] = new int[]{10, 22, 34, 37};
        waves[52] = new int[]{10, 22, 34, 37};
        waves[53] = new int[]{10, 20, 32, 35, 39};
        waves[54] = new int[]{10, 16, 22, 28, 34, 40};
        waves[55] = new int[]{10, 14, 18};
        waves[56] = new int[]{10, 14, 18};
        waves[57] = new int[]{10, 22, 34, 37, 38};
        waves[58] = NORMAL;
        waves[59] = new int[]{10, 20, 30, 33};

        for (int round = 61; round <= 73; round++) waves[round - 1] = NORMAL;
        waves[73] = new int[]{10, 14, 18, 22, 27, 32};
        waves[74] = new int[]{10, 14, 18, 22, 27, 32};
        for (int round = 76; round <= 100; round++) waves[round - 1] = NORMAL;
        for (int round = 101; round <= 105; round++) waves[round - 1] = new int[]{5};
        return waves;
    }

    static int[] waveTimes(int round) {
        return round < 1 || round > WAVES.length ? new int[0] : WAVES[round - 1];
    }

    static int waveAt(int round, long elapsedMs) {
        int[] times = waveTimes(round);
        int wave = 0;
        for (int index = 0; index < times.length; index++) {
            if (elapsedMs >= times[index] * 1_000L) wave = index + 1;
        }
        return wave;
    }

    static String fallbackLine(int round, long elapsedMs) {
        int[] times = waveTimes(round);
        if (times.length == 0) return null;
        int wave = waveAt(round, elapsedMs);
        if (wave >= times.length) return "Wave complete";
        long remaining = Math.max(0L, times[wave] * 1_000L - elapsedMs);
        return "Wave " + (wave + 1) + "/" + times.length + " | next "
                + String.format(java.util.Locale.ROOT, "%.1fs", remaining / 1_000.0);
    }
}
