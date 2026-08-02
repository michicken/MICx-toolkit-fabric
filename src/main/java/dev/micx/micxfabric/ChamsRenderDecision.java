package dev.micx.micxfabric;

/** Minecraft-free decision for replacing one vanilla model submit with Chams. */
final class ChamsRenderDecision {
    enum Result { VANILLA, APPLY }

    private ChamsRenderDecision() {
    }

    static Result decide(boolean enabled, boolean hasWorld, boolean hasPlayer,
                         boolean target, double distanceSq, int range,
                         boolean blocked, boolean activeRender) {
        if (!enabled || !hasWorld || !hasPlayer || !target || blocked == false || activeRender) {
            return Result.VANILLA;
        }
        int safeRange = ChamsModule.clampRange(range);
        if (!Double.isFinite(distanceSq) || distanceSq < 0.0
                || distanceSq > (double) safeRange * safeRange) {
            return Result.VANILLA;
        }
        return Result.APPLY;
    }
}
