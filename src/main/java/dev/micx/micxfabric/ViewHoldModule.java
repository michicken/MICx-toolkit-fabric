package dev.micx.micxfabric;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * ViewHold（Forge 移植）：按住绑定键立即切到目标视角（背后/正面），松开恢复第一人称。
 * 玩家自己按 F5 切的第三人称不干预（ViewHoldState managing 门控）。
 *
 * <p>Pitch Mirror（俯仰镜像，仅正面视角）：26.2 无 RenderTickEvent，
 * 渲染帧包裹改用 GameRenderer.renderLevel HEAD/RETURN Mixin——HEAD 取反 pitch
 * （相机与实体模型同帧生效），RETURN 线性补偿恢复 + clamp ±90（Grim 安全）。
 */
public final class ViewHoldModule implements Module {
    private static final ViewHoldModule INSTANCE = new ViewHoldModule();

    private final ViewHoldState state = new ViewHoldState(ViewHoldState.VIEW_BEHIND);
    private InputBinding binding;
    private boolean enabled;
    private boolean configLoaded;
    /** Pitch Mirror 开关（正面视角时把渲染帧内 pitch 取反）。 */
    private boolean pitchMirror = true;
    /* ---- 渲染帧镜像状态 ---- */
    private boolean mirrorActive;
    private float mirrorSavedPitch;
    private float mirrorSavedPrevPitch;

    private ViewHoldModule() {
    }

    public static ViewHoldModule instance() {
        return INSTANCE;
    }

    @Override public String id() { return "view_hold"; }
    @Override public boolean defaultEnabled() { return true; }
    @Override public boolean enabled() { return enabled; }

    @Override public void setEnabled(boolean v) {
        loadConfig();
        if (v && !enabled && binding != null && binding.isUnbound()) {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.player != null) {
                client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§e[MICx] ViewHold 未绑定按键，请在面板 KEYBIND 区绑定"));
            }
        }
        enabled = v;
        ModuleStateStore.put(id(), v);
        if (!v && state.isManaging()) {
            restoreFirstPerson();
            state.reset();
        }
    }

    @Override
    public InputBinding primaryBinding() {
        loadConfig();
        return binding;
    }

    public void setKeyCode(int code) {
        loadConfig();
        binding.setCode(code);
        saveConfig();
    }

    /** 目标视角（1=背后 / 2=正面），配置面板调用。 */
    public int getTargetView() {
        loadConfig();
        return state.target();
    }

    public void setTargetView(int view) {
        loadConfig();
        state.setTarget(view);
        saveConfig();
    }

    /** Pitch Mirror 开关（配置面板调用）。 */
    public boolean isPitchMirror() {
        loadConfig();
        return pitchMirror;
    }

    public void setPitchMirror(boolean v) {
        pitchMirror = v;
        if (!v) renderLevelEnd();
        saveConfig();
    }

    /* ---- Pitch Mirror 渲染帧包裹（MixinGameRendererViewHold 调用） ---- */

    /** renderLevel HEAD：正面视角管理中 → 取反 pitch（含插值旧值防头部抖动）。 */
    public void renderLevelStart() {
        if (!enabled || !pitchMirror) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (!state.isManaging() || !ViewHoldState.pitchMirrorActive(state.target())) return;
        if (mirrorActive) {
            // 上一帧 renderLevel 中途异常跳过了 RETURN：当前 pitch 还在镜像域，
            // 先按保存值补偿恢复回真实域，否则会把镜像值当真实值存档导致视角永久翻转
            mc.player.setXRot(net.minecraft.util.Mth.clamp(
                    ViewHoldState.unmirrorPitch(mc.player.getXRot(), mirrorSavedPitch), -90.0f, 90.0f));
            mc.player.xRotO = net.minecraft.util.Mth.clamp(
                    ViewHoldState.unmirrorPitch(mc.player.xRotO, mirrorSavedPrevPitch), -90.0f, 90.0f);
            mirrorActive = false;
        }
        mirrorSavedPitch = mc.player.getXRot();
        mirrorSavedPrevPitch = mc.player.xRotO;
        mc.player.setXRot(-mirrorSavedPitch);
        mc.player.xRotO = -mirrorSavedPrevPitch;
        mirrorActive = true;
    }

    /** renderLevel RETURN：线性补偿恢复（保留帧内鼠标增量），clamp ±90。 */
    public void renderLevelEnd() {
        if (!mirrorActive) return;
        mirrorActive = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        float restored = net.minecraft.util.Mth.clamp(
                ViewHoldState.unmirrorPitch(mc.player.getXRot(), mirrorSavedPitch), -90.0f, 90.0f);
        mc.player.setXRot(restored);
        mc.player.xRotO = net.minecraft.util.Mth.clamp(
                ViewHoldState.unmirrorPitch(mc.player.xRotO, mirrorSavedPrevPitch), -90.0f, 90.0f);
    }

    @Override public void tick(Minecraft client) {
        if (!enabled || client == null || client.getWindow() == null) return;
        loadConfig();
        boolean blocked = client.gui.screen() != null || client.player == null;
        boolean keyDown = !blocked && binding.down(client);
        int action = state.observe(keyDown, blocked);
        if (action == ViewHoldState.NO_ACTION) return;
        applyCameraType(client, action);
    }

    private static void applyCameraType(Minecraft client, int view) {
        CameraType type = switch (view) {
            case ViewHoldState.VIEW_BEHIND -> CameraType.THIRD_PERSON_BACK;
            case ViewHoldState.VIEW_FRONT -> CameraType.THIRD_PERSON_FRONT;
            default -> CameraType.FIRST_PERSON;
        };
        if (client.options.getCameraType() != type) {
            client.options.setCameraType(type);
        }
    }

    private void restoreFirstPerson() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.options.getCameraType() != CameraType.FIRST_PERSON) {
            client.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    @Override public void resetInput() {
        if (state.isManaging()) {
            restoreFirstPerson();
            state.reset();
        }
    }

    @Override public void resetState() {
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path cur = FabricRuntime.configPath().resolve("view-hold.properties");
        Path leg = FabricRuntime.configPath().getParent().resolve("MICxToolkit_ViewHold.cfg");
        Properties p = ConfigProperties.load(cur, leg);
        int target = ConfigProperties.integer(p, "targetView", ViewHoldState.VIEW_BEHIND, 1, 2);
        state.setTarget(target);
        pitchMirror = ConfigProperties.bool(p, "pitchMirror", true);
        int code = ConfigProperties.integer(p, "toggleKeyCode", 0, -108, org.lwjgl.glfw.GLFW.GLFW_KEY_LAST);
        binding = new InputBinding(code);
    }

    private void saveConfig() {
        Properties p = new Properties();
        p.setProperty("targetView", Integer.toString(state.target()));
        p.setProperty("pitchMirror", Boolean.toString(pitchMirror));
        p.setProperty("toggleKeyCode", Integer.toString(binding == null ? 0 : binding.code()));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("view-hold.properties"), p, "MICx ViewHold");
        } catch (IOException e) {
            MicxFabric.LOGGER.warn("Unable to save ViewHold configuration", e);
        }
    }
}
