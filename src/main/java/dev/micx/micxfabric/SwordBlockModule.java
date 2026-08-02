package dev.micx.micxfabric;

/**
 * 1.7 剑格挡动画模块开关。
 *
 * <p>启用后手持剑按右键会进入 1.8.9 风格的 BLOCK 使用状态（vanilla 26.2 剑返回 NONE），
 * 配合 MixinItemInHandRenderer 显示 1.7 格挡姿态。
 */
public final class SwordBlockModule implements Module {
    private static final SwordBlockModule INSTANCE = new SwordBlockModule();
    private boolean enabled = true;

    private SwordBlockModule() {
    }

    public static SwordBlockModule instance() {
        return INSTANCE;
    }

    public static boolean isEnabled() {
        return INSTANCE.enabled;
    }

    public static void toggle() {
        INSTANCE.setEnabled(!INSTANCE.enabled);
    }

    @Override
    public String id() {
        return "sword_block";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        ModuleStateStore.put(id(), enabled);
    }
}
