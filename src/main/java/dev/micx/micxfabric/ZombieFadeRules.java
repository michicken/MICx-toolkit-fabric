package dev.micx.micxfabric;

public final class ZombieFadeRules {
    public static final double DEFAULT_RADIUS_BLOCKS = 3.0D;
    public static final double RADIUS_MIN = 0.5D, RADIUS_MAX = 8.0D;
    public static final float ALPHA = 0.30F;
    private static double radiusBlocks = DEFAULT_RADIUS_BLOCKS;
    private ZombieFadeRules() {}
    public static double radiusBlocks() { return radiusBlocks; }
    public static void setRadiusBlocks(double r) { radiusBlocks = Math.max(RADIUS_MIN, Math.min(RADIUS_MAX, r)); }
    public static boolean isWithinRadiusSq(double d2) { double r = radiusBlocks; return d2 >= 0.0D && d2 <= r * r; }
    public static boolean shouldFade(boolean hostile, boolean dead, int deathTime) { return hostile && !dead && deathTime <= 0; }
    public static float alpha() { return ALPHA; }
}
