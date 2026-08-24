package dev.micx.micxfabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Shared helper for submitting through-wall line geometry from COLLECT_SUBMITS.
 * Coordinates are already camera-relative (caller subtracts the camera position).
 * Vertex layout matches the ESP lines pipeline (POSITION_COLOR, LINES pairs).
 */
public final class WorldLines {

    private WorldLines() {
    }

    /**
     * Submits line segments. {@code segments} is a flat array of local-space
     * endpoints: [x0,y0,z0, x1,y1,z1, ...]. Odd segment counts ignore the tail.
     */
    public static void submit(SubmitNodeCollector collector, PoseStack pose,
                              RenderType renderType, double[] segments, int argb) {
        if (segments == null || segments.length < 6) return;
        collector.submitCustomGeometry(pose, renderType, (stackPose, consumer) -> {
            for (int i = 0; i + 5 < segments.length; i += 6) {
                vertex(consumer, stackPose, segments[i], segments[i + 1], segments[i + 2], argb);
                vertex(consumer, stackPose, segments[i + 3], segments[i + 4], segments[i + 5], argb);
            }
        });
    }

    /** Ground X marker at (x,y,z) with the given half length. */
    public static void appendCrossX(double[] out, int cursor, double x, double y, double z,
                                    double half, int argb) {
        out[cursor]     = x - half; out[cursor + 1] = y; out[cursor + 2] = z - half;
        out[cursor + 3] = x + half; out[cursor + 4] = y; out[cursor + 5] = z + half;
        out[cursor + 6] = x - half; out[cursor + 7] = y; out[cursor + 8] = z + half;
        out[cursor + 9] = x + half; out[cursor + 10] = y; out[cursor + 11] = z - half;
    }

    /** Vertical pillar (y → y+height) plus a short T bar on top, for spawn markers. */
    public static void appendColumn(double[] out, int cursor, double x, double y, double z,
                                    double height, double topHalf, int argb) {
        out[cursor]     = x; out[cursor + 1] = y;          out[cursor + 2] = z;
        out[cursor + 3] = x; out[cursor + 4] = y + height; out[cursor + 5] = z;
        out[cursor + 6] = x - topHalf; out[cursor + 7] = y + height; out[cursor + 8] = z;
        out[cursor + 9] = x + topHalf; out[cursor + 10] = y + height; out[cursor + 11] = z;
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               double x, double y, double z, int argb) {
        consumer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24);
    }
}
