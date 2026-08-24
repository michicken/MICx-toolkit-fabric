package dev.micx.micxfabric;

/**
 * 快捷视角切换状态机（纯逻辑，可单测）。Forge 同名类 1:1 移植。
 * 只有"本模块按下过"（managing）才负责恢复第一人称；玩家自己按 F5 不干预。
 */
public final class ViewHoldState {

    /** 不干预：玩家自己按 F5 造成的视角状态，不属于本模块管理范围。 */
    public static final int NO_ACTION = -1;
    /** 背后视角。 */
    public static final int VIEW_BEHIND = 1;
    /** 正面视角。 */
    public static final int VIEW_FRONT = 2;

    public static boolean isValidTarget(int view) {
        return view == VIEW_BEHIND || view == VIEW_FRONT;
    }

    /** 俯仰镜像是否生效：仅正面视角模式（低头抬头都持续镜像；向下看为正）。 */
    public static boolean pitchMirrorActive(int targetView) {
        return targetView == VIEW_FRONT;
    }

    /**
     * 渲染帧结束的恢复公式：鼠标视角输入在渲染帧内叠加在镜像值上
     * （当前 = -saved + d），恢复目标为真实域（saved + d），
     * 故 恢复值 = 当前值 + 2×保存值（线性补偿，d 任意大均精确）。
     * 调用方需将结果 clamp 到 ±90。
     */
    public static float unmirrorPitch(float currentPitch, float savedPitch) {
        return currentPitch + 2f * savedPitch;
    }

    private int target;
    private boolean managing;

    public ViewHoldState(int target) {
        if (!isValidTarget(target)) {
            throw new IllegalArgumentException("target must be 1 or 2, got " + target);
        }
        this.target = target;
    }

    /**
     * 输入当前 tick 的按键状态，返回应设置的视角值：
     * target / 0（恢复第一人称）/ {@link #NO_ACTION}（不干预）。
     */
    public int observe(boolean keyDown, boolean blocked) {
        if (!blocked && keyDown) {
            managing = true;
            return target;
        }
        if (managing) {
            managing = false;
            return 0;
        }
        return NO_ACTION;
    }

    public boolean isManaging() {
        return managing;
    }

    public int target() {
        return target;
    }

    public void setTarget(int view) {
        if (!isValidTarget(view)) {
            throw new IllegalArgumentException("target must be 1 or 2, got " + view);
        }
        target = view;
    }

    public void reset() {
        managing = false;
    }
}
