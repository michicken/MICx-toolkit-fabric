package dev.micx.micxfabric;

/** Minecraft-free decision for replacing one vanilla model submit with Chams. */
final class ChamsRenderDecision {
    enum Result { VANILLA, APPLY }

    private ChamsRenderDecision() {
    }

    static Result decide(boolean enabled, boolean hasWorld, boolean hasPlayer,
                         boolean target, double distanceSq, int range,
                         boolean blocked, boolean activeRender) {
        // v4：遮挡与否不再参与决策（独立目标 + 帧末合成天然穿墙，
        // 可见实体合成像素与 vanilla 一致无感）。blocked 参数保留仅为签名兼容。
        if (!enabled || !hasWorld || !hasPlayer || !target || activeRender) {
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
