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
 * <p>与 Forge 版差异：Pitch Mirror（正面视角俯仰镜像）依赖渲染帧内取反/恢复，
 * 26.2 Fabric 无对应渲染帧事件，暂不移植——仅按住切视角。
 */
public final class ViewHoldModule implements Module {
    private static final ViewHoldModule INSTANCE = new ViewHoldModule();

    private final ViewHoldState state = new ViewHoldState(ViewHoldState.VIEW_BEHIND);
    private InputBinding binding;
    private boolean enabled;
    private boolean configLoaded;

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
        int code = ConfigProperties.integer(p, "toggleKeyCode", 0, -108, org.lwjgl.glfw.GLFW.GLFW_KEY_LAST);
        binding = new InputBinding(code);
    }

    private void saveConfig() {
        Properties p = new Properties();
        p.setProperty("targetView", Integer.toString(state.target()));
        p.setProperty("toggleKeyCode", Integer.toString(binding == null ? 0 : binding.code()));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("view-hold.properties"), p, "MICx ViewHold");
        } catch (IOException e) {
            MicxFabric.LOGGER.warn("Unable to save ViewHold configuration", e);
        }
    }
}
