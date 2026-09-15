package dev.micx.micxfabric;

/**
 * 服务端口径的命中箱表。
 *
 * <p>数值来源：OceanClient 3.3.8 的 {@code Aimbot.mappedDimsOrNull}（1.8.9 Hypixel 实测）。
 * 它的取值普遍<b>大于</b> 1.8 客户端渲染盒（僵尸 0.9 宽 vs 原版 0.6、狼 1.5 vs 0.6、
 * 铁傀儡 1.8 vs 1.4），说明 Hypixel 服务端的命中判定箱比客户端 AABB 宽——命中箱是
 * 服务端属性，所以这套数值对 26.2 同样适用（用户 2026-09-15 定稿：直接采用）。
 *
 * <p>用法：客户端 AABB 只当兜底。表里没有的怪（或表值为空）保持原样，
 * 由 {@link #resolve} 决定；全局缩放 {@code scale} 供实测微调（1.0 = 原表值）。
 */
public final class AimTargetDims {

    /** 一个命中箱的三围（格）。 */
    public record Dims(double width, double height, double depth) {
    }

    /** 史莱姆/岩浆怪：边长 = 0.51 × size（与 OceanClient 的 getDims 一致）。 */
    private static final double CUBE_PER_SIZE = 0.51;

    private AimTargetDims() {
    }

    /**
     * 按目标键取服务端口径；未收录返回 {@code null}。
     *
     * <p>键：{@code zombie / zombie_baby / zombified_piglin / skeleton / wither_skeleton /
     * blaze / wolf / wolf_baby / silverfish / endermite / witch / creeper / cave_spider /
     * giant / ghast / iron_golem / slime:<size>}。
     */
    public static Dims forKey(String key) {
        if (key == null || key.isEmpty()) return null;
        if (key.startsWith("slime:")) {
            Integer size = parseInt(key.substring(6));
            if (size == null) return null;
            double edge = CUBE_PER_SIZE * Math.max(1, size);
            return new Dims(edge, edge, edge);
        }
        return switch (key) {
            case "zombie" -> new Dims(0.9, 2.0, 0.9);
            case "zombie_baby" -> new Dims(1.2, 0.8, 1.2);
            case "zombified_piglin" -> new Dims(0.9, 2.0, 0.9);
            case "skeleton" -> new Dims(0.9, 2.0, 0.9);
            case "wither_skeleton" -> new Dims(1.0, 2.6, 1.0);
            case "blaze" -> new Dims(0.9, 2.0, 0.9);
            case "wolf" -> new Dims(1.5, 0.7, 1.5);
            case "wolf_baby" -> new Dims(0.5, 0.3, 0.5);
            case "silverfish" -> new Dims(0.2, 0.4, 0.2);
            case "endermite" -> new Dims(0.2, 0.4, 0.2);
            case "witch" -> new Dims(0.9, 1.75, 0.9);
            case "creeper" -> new Dims(0.9, 1.1, 0.9);
            case "cave_spider" -> new Dims(0.5, 0.7, 0.5);
            case "giant" -> new Dims(3.9, 12.0, 3.9);
            case "ghast" -> new Dims(4.4, 4.4, 4.4);
            case "iron_golem" -> new Dims(1.8, 2.7, 1.8);
            default -> null;
        };
    }

    /**
     * 最终命中箱：表里有就用表值，没有就用客户端 AABB 的三围；再统一乘 {@code scale}。
     * 缩放后每边至少保留 {@code MIN_EDGE} 格，避免调参把盒子缩没。
     */
    public static Dims resolve(String key, Dims fallback, double scale) {
        Dims base = forKey(key);
        if (base == null) base = fallback;
        if (base == null) return null;
        if (!(scale > 0.0) || scale == 1.0) return clampEdges(base);
        return clampEdges(new Dims(base.width() * scale, base.height() * scale, base.depth() * scale));
    }

    private static Dims clampEdges(Dims dims) {
        return new Dims(
                Math.max(MIN_EDGE, dims.width()),
                Math.max(MIN_EDGE, dims.height()),
                Math.max(MIN_EDGE, dims.depth()));
    }

    /** 命中箱任一边不小于这个值，防止缩放调参把盒子压成一条线。 */
    public static final double MIN_EDGE = 0.05;

    private static Integer parseInt(String text) {
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
