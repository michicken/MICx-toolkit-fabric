package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/** ZoomScope 放大镜 — 按住放大，照抄 {@link ViewHoldModule} 单键绑定。 */
public final class ZoomScopeModule implements Module {

    private static final ZoomScopeModule INSTANCE = new ZoomScopeModule();

    private boolean enabled;
    private boolean configLoaded;
    private final ZoomScopeState state = new ZoomScopeState(ZoomScopeState.DEFAULT_ZOOM);
    private InputBinding binding;
    private int zoomFactor = ZoomScopeState.DEFAULT_ZOOM;
    private float sensitivityK = 1.0f;

    private ZoomScopeModule() {}

    public static ZoomScopeModule instance() { return INSTANCE; }

    @Override public String id() { return "zoom_scope"; }
    @Override public boolean defaultEnabled() { return true; }
    @Override public boolean enabled() { return enabled; }

    @Override public void setEnabled(boolean v) {
        loadConfig();
        enabled = v;
        ModuleStateStore.put(id(), v);
        if (!v) state.reset();
    }

    @Override public InputBinding primaryBinding() {
        loadConfig();
        return binding;
    }

    public void setKeyCode(int code) {
        loadConfig();
        binding.setCode(code);
        saveConfig();
    }

    public int zoomFactor() { loadConfig(); return zoomFactor; }
    public float sensitivityK() { loadConfig(); return sensitivityK; }

    public void setSensitivityK(float k) {
        k = Math.max(0.5f, Math.min(3.0f, k));
        loadConfig();
        if (Math.abs(k - sensitivityK) < 0.001f) return;
        sensitivityK = k;
        state.setSensitivityK(k);
        saveConfig();
    }

    public ZoomScopeState state() { return state; }
    public boolean isActive() { return enabled && state.isActive(); }

    @Override public void tick(Minecraft client) {
        if (!enabled || client == null || client.getWindow() == null) return;
        loadConfig();
        if (binding == null || binding.isUnbound()) return;
        boolean blocked = client.gui.screen() != null || client.player == null;
        boolean keyDown = !blocked && binding.down(client);
        state.observe(keyDown, blocked);
    }

    public boolean onScroll(double amount) {
        if (!enabled || state == null || !state.isActive()) return false;
        int z = state.adjustZoom(amount > 0 ? 1 : amount < 0 ? -1 : 0);
        zoomFactor = z;
        saveConfig();
        return true;
    }

    @Override public void resetInput() { state.reset(); }
    @Override public void resetState() { state.reset(); }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path cur = FabricRuntime.configPath().resolve("zoom-scope.properties");
        Path leg = FabricRuntime.configPath().getParent().resolve("MICxToolkit_ZoomScope.cfg");
        Properties p = ConfigProperties.load(cur, leg);
        int code = ConfigProperties.integer(p, "toggleKeyCode", 0, -108, GLFW.GLFW_KEY_LAST);
        // 兼容旧 KeyChord 存储
        if (code == 0) {
            int[] chord = KeyChord.readConfig(p, "toggleKeys", "toggleKey", 0);
            if (!KeyChord.isEmpty(chord)) code = KeyChord.primary(chord);
        }
        binding = new InputBinding(code);
        zoomFactor = ConfigProperties.integer(p, "zoomFactor", ZoomScopeState.DEFAULT_ZOOM, ZoomScopeState.MIN_ZOOM, ZoomScopeState.MAX_ZOOM);
        sensitivityK = (float) ConfigProperties.real(p, "sensitivityK", 1.0, 0.5, 3.0);
        state.observe(false, true);
        try {
            java.lang.reflect.Field f = ZoomScopeState.class.getDeclaredField("zoom");
            f.setAccessible(true);
            f.setInt(state, zoomFactor);
        } catch (Throwable ignored) { state.adjustZoom(zoomFactor - state.zoom()); }
        state.setSensitivityK(sensitivityK);
    }

    private void saveConfig() {
        Properties p = new Properties();
        p.setProperty("toggleKeyCode", Integer.toString(binding == null ? 0 : binding.code()));
        // 旧字段保留便于回退
        if (binding != null) KeyChord.writeConfig(p, "toggleKeys", "toggleKey", KeyChord.single(binding.code()));
        p.setProperty("zoomFactor", Integer.toString(zoomFactor));
        p.setProperty("sensitivityK", Float.toString(sensitivityK));
        try { AtomicProperties.store(FabricRuntime.configPath().resolve("zoom-scope.properties"), p, "MICx ZoomScope"); }
        catch (IOException e) { MicxFabric.LOGGER.warn("Unable to save ZoomScope config", e); }
    }
}
