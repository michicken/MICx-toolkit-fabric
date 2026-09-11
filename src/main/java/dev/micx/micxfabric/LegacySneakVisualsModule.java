package dev.micx.micxfabric;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 1.8 潜行眼高：把本地潜行眼高抬回 1.8 的 1.54，碰撞保持不变（1.5 格缝照样能钻）。
 *
 * <p>26.2 CROUCHING eyeHeight=1.27（STANDING 1.62），落差 0.35；1.8 潜行只降 0.08。
 * 本模块在逻辑保持 CROUCHING 碰撞的前提下，把<b>眼高真值</b>统一抬回 1.54（+0.27）。
 *
 * <p><b>「眼高真值」的四个消费方必须一致</b>，否则会产生系统性偏差：
 * <ul>
 *   <li>{@code Camera.eyeHeight}（第一人称视觉）—— {@code MixinCameraLegacySneak}</li>
 *   <li>{@code Entity.getEyeHeight()}（<b>本地命中射线的起点</b>：
 *       {@code Minecraft.pick → LocalPlayer.raycastHitResult} 读 {@code getEyePosition()}）
 *       —— {@code MixinEntityLegacySneakEyeHeight}</li>
 *   <li>aimbot 的瞄算眼位（读的正是 {@code getEyePosition()}，因此自动跟随）</li>
 *   <li>1.8.9 服务端的复核射线（{@code EntityPlayer.getEyeHeight()} 恒为 1.54）</li>
 * </ul>
 *
 * <p>早期实现<b>只抬了相机</b>，于是潜行时三者错开 0.27 格：准心（相机 1.54）压在目标
 * 上方、本地 pick（1.27）却仍能命中、服务端复核（1.54）判空 —— 表现为「潜行后瞄空」。
 * 现改为统一改眼高：相机 / 本地 pick / aim 计算 / 服务端四方一致。
 *
 * <p>只改眼高，<b>不改碰撞尺寸与 pose</b>；该字段是纯本地量、不下发，服务端/反作弊/Via 不受影响。
 */
public final class LegacySneakVisualsModule implements Module {
    private static final LegacySneakVisualsModule INSTANCE = new LegacySneakVisualsModule();
    private boolean enabled;
    private boolean configLoaded;

    /** 潜行视觉补偿：现代潜行 1.27 → 1.8 潜行 1.54。 */
    public static final float VISUAL_SNEAK_EYE_HEIGHT = 1.54f;
    /** 现代潜行碰撞对应的 eyeHeight（源真值，用于判定“是否处于现代潜行眼高”）。 */
    public static final float MODERN_SNEAK_EYE_HEIGHT = 1.27f;
    /** 抬升量。 */
    public static final float DELTA_Y = VISUAL_SNEAK_EYE_HEIGHT - MODERN_SNEAK_EYE_HEIGHT; // 0.27

    /**
     * 有效眼高真值：启用本模块且处于潜行姿态时取 1.8 的 {@link #VISUAL_SNEAK_EYE_HEIGHT}，
     * 否则回退到给定值（实体的现代 pose 眼高）。纯函数，便于回归测试。
     *
     * <p>由 {@code MixinEntityLegacySneakEyeHeight} 在 {@code Entity.getEyeHeight()} 中调用，
     * 使本地命中射线与相机/服务端共用同一个眼高。
     *
     * @param moduleEnabled      本模块是否启用
     * @param crouching          是否处于 {@code Pose.CROUCHING}
     * @param fallbackEyeHeight  未命中补偿条件时返回的眼高（实体的现代 pose 眼高）
     */
    public static float effectiveEyeHeight(boolean moduleEnabled, boolean crouching,
                                           float fallbackEyeHeight) {
        return moduleEnabled && crouching ? VISUAL_SNEAK_EYE_HEIGHT : fallbackEyeHeight;
    }

    private LegacySneakVisualsModule() {}

    public static LegacySneakVisualsModule instance() { return INSTANCE; }

    @Override public String id() { return "legacy_sneak_visuals"; }
    @Override public boolean defaultEnabled() { return false; }
    @Override public boolean enabled() { return enabled; }

    @Override public void setEnabled(boolean v) {
        loadConfig();
        enabled = v;
        ModuleStateStore.put(id(), v);
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
    }

    private void saveConfig() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("legacy-sneak-visuals.properties"), p,
                    "MICx LegacySneakVisuals");
        } catch (IOException e) {
            MicxFabric.LOGGER.warn("Unable to save LegacySneakVisuals configuration", e);
        }
    }
}
