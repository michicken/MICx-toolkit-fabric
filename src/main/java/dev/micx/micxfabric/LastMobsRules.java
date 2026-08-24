package dev.micx.micxfabric;

/**
 * LastMobs 纯规则（Forge 同名类移植）：剩余怪数 ≤ N 时准心拉线。
 * 投影改为相机基向量 + 垂直 FOV 数学（26.2 无 ActiveRenderInfo 可反射，
 * 由模块提供 yaw/pitch/fov/aspect），语义与 Forge gluProject 等价。
 */
public final class LastMobsRules {

    /** 触发判定：剩余怪数 ∈ [1, maxCount] 才画线（-1 = 数据不可用）。 */
    public static boolean isActive(int zombiesLeft, int maxCount) {
        return zombiesLeft >= 1 && zombiesLeft <= maxCount;
    }

    /**
     * 世界坐标偏移 → scaled GUI 屏幕坐标。
     *
     * @param dx,dy,dz 目标点相对相机（眼睛）的位移
     * @param yawDeg   相机 yaw（MC 语义：0 = +Z）
     * @param pitchDeg 相机 pitch
     * @param fovYDeg  垂直 FOV（度）
     * @param aspect   宽高比
     * @return {x, y}；目标在相机背后或方向退化时返回 null
     */
    public static float[] project(double dx, double dy, double dz,
                                  float yawDeg, float pitchDeg, float fovYDeg,
                                  double aspect, int width, int height) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);
        // MC look vector: x=-sin(yaw)cos(pitch), y=-sin(pitch), z=cos(yaw)cos(pitch)
        double fx = -Math.sin(yaw) * cosPitch;
        double fy = -sinPitch;
        double fz = Math.cos(yaw) * cosPitch;
        // right = normalize((-fz, 0, fx))；正对天/地时水平长度为 0，无法定义右向
        double horizontal = Math.sqrt(fx * fx + fz * fz);
        if (horizontal < 1.0E-6) return null;
        double rx = -fz / horizontal;
        double rz = fx / horizontal;
        // trueUp = right × forward
        double ux = -rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy;

        double zCam = dx * fx + dy * fy + dz * fz;
        if (zCam < 0.05D) return null;   // 相机背后（或贴脸退化）
        double xCam = dx * rx + dz * rz;
        double yCam = dx * ux + dy * uy + dz * uz;
        double tanHalfFov = Math.tan(Math.toRadians(fovYDeg) * 0.5D);

        double ndcX = xCam / (zCam * tanHalfFov * aspect);
        double ndcY = yCam / (zCam * tanHalfFov);
        return new float[]{
                (float) (width * 0.5D + ndcX * width * 0.5D),
                (float) (height * 0.5D - ndcY * height * 0.5D),
        };
    }

    /** 屏幕点夹取到可视区边缘内（margin px），屏幕外怪仍给出方向指示。 */
    public static float[] clampToScreen(float x, float y, int width, int height, int margin) {
        float cx = x < margin ? margin : (x > width - margin ? width - margin : x);
        float cy = y < margin ? margin : (y > height - margin ? height - margin : y);
        return new float[]{cx, cy};
    }

    private LastMobsRules() {
    }
}
