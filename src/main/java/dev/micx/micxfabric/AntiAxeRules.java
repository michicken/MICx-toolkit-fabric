package dev.micx.micxfabric;

/** Pure rules shared by the in-toolkit and lightweight AntiAXE builds. */
public final class AntiAxeRules {

    public static final long CLAIM_WINDOW_MS = 10_500L;

    public enum ChatAction { NONE, ARM, DISARM }

    private AntiAxeRules() { }

    public static ChatAction classifyChat(String text) {
        if (text == null) return ChatAction.NONE;
        String clean = text.replaceAll("\u00a7[0-9A-FK-ORa-fk-or]", "").trim();
        if (clean.startsWith("You found The Puncher in the Lucky Chest!")) {
            return ChatAction.ARM;
        }
        if (clean.startsWith("You found ") && clean.contains(" in the Lucky Chest!")) {
            return ChatAction.DISARM;
        }
        if (clean.startsWith("You claimed ") && clean.contains(" in the Lucky Chest!")) {
            return ChatAction.DISARM;
        }
        return ChatAction.NONE;
    }

    public static boolean isArmed(long now, long armedUntil) {
        return armedUntil > 0L && now >= 0L && now < armedUntil;
    }

    /** Segment/AABB slab test kept free of Minecraft classes for deterministic tests. */
    public static boolean intersects(double sx, double sy, double sz,
                                     double ex, double ey, double ez,
                                     double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ) {
        double[] start = {sx, sy, sz};
        double[] delta = {ex - sx, ey - sy, ez - sz};
        double[] min = {minX, minY, minZ};
        double[] max = {maxX, maxY, maxZ};
        double lo = 0.0, hi = 1.0;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(delta[axis]) < 1.0E-9) {
                if (start[axis] < min[axis] || start[axis] > max[axis]) return false;
                continue;
            }
            double a = (min[axis] - start[axis]) / delta[axis];
            double b = (max[axis] - start[axis]) / delta[axis];
            if (a > b) { double swap = a; a = b; b = swap; }
            lo = Math.max(lo, a);
            hi = Math.min(hi, b);
            if (lo > hi) return false;
        }
        return true;
    }
}
