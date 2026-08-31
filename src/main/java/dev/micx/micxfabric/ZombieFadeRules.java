package dev.micx.micxfabric;

public final class ZombieFadeRules {
    public static final double DEFAULT_RADIUS_BLOCKS = 3.0D;
    public static final double RADIUS_MIN = 0.5D, RADIUS_MAX = 8.0D;
    public static final float DEFAULT_ALPHA = 0.30F;
    public static final float ALPHA_MIN = 0.05F, ALPHA_MAX = 1.0F;
    private static double radiusBlocks = DEFAULT_RADIUS_BLOCKS;
    private static float alpha = DEFAULT_ALPHA;
    private ZombieFadeRules() {}
    public static double radiusBlocks() { return radiusBlocks; }
    public static void setRadiusBlocks(double r) { radiusBlocks = Math.max(RADIUS_MIN, Math.min(RADIUS_MAX, r)); }
    public static boolean isWithinRadiusSq(double d2) { double r = radiusBlocks; return d2 >= 0.0D && d2 <= r * r; }
    public static boolean shouldFade(boolean hostile, boolean dead, int deathTime) { return hostile && !dead && deathTime <= 0; }
    /** 淡化目标不透明度（越小越透明），面板滑条可调。 */
    public static float alpha() { return alpha; }
    public static void setAlpha(float a) { alpha = Math.max(ALPHA_MIN, Math.min(ALPHA_MAX, a)); }
}
