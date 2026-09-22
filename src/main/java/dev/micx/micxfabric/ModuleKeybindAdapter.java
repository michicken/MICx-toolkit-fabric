package dev.micx.micxfabric;

import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Bridges a real module primary binding into the panel's editable keybind contract. */
public final class ModuleKeybindAdapter implements ModuleKeybind {
    private final String moduleId;
    private final Supplier<InputBinding> getter;
    private final IntConsumer setter;
    /** 按住语义的模块（zoom_scope/view_hold 自有轮询）不支持组合键，保持单键即时提交。 */
    private final boolean chordSupported;
    private String description;

    public ModuleKeybindAdapter(
            String moduleId,
            String description,
            Supplier<InputBinding> getter,
            IntConsumer setter
    ) {
        this(moduleId, description, getter, setter, true);
    }

    public ModuleKeybindAdapter(
            String moduleId,
            String description,
            Supplier<InputBinding> getter,
            IntConsumer setter,
            boolean chordSupported
    ) {
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        this.description = Objects.requireNonNull(description, "description");
        this.getter = Objects.requireNonNull(getter, "getter");
        this.setter = Objects.requireNonNull(setter, "setter");
        this.chordSupported = chordSupported;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = Objects.requireNonNull(description, "description");
    }

    @Override
    public int keyCode() {
        InputBinding binding = getter.get();
        return binding == null ? GLFW.GLFW_KEY_UNKNOWN : binding.code();
    }

    @Override
    public String keyLabel() {
        // 组合键优先展示（面板侧覆盖自带单键；2026-09-22 起单键模块也可录组合键）。
        // ModulePanelRegistry 的静态初始化在单测环境不可用（LinkageError），回落单键展示。
        try {
            int[] chord = ModulePanelRegistry.panelChord(moduleId);
            if (chord != null && !KeyChord.isEmpty(chord)) return KeyChord.display(chord);
        } catch (RuntimeException | LinkageError ignored) {
            // fall through to single-key label
        }
        InputBinding binding = getter.get();
        if (binding == null || binding.isUnbound()) return "未绑定";
        return KeyChord.keyName(binding.code());
    }

    @Override
    public boolean supportsChord() {
        return chordSupported;
    }

    @Override
    public void setChordCodes(int[] codes) {
        ModulePanelRegistry.setPanelChord(moduleId, codes);
        HotkeyRuntime.clearModule(moduleId);
        HotkeyRuntimeReleased.clearModule(moduleId);
    }

    @Override
    public void setKeyCode(int keyCode) {
        int normalized = keyCode == 0
                ? GLFW.GLFW_KEY_UNKNOWN
                : Math.max(-108, Math.min(GLFW.GLFW_KEY_LAST, keyCode));
        setter.accept(normalized);
        HotkeyRuntime.clearModule(moduleId);
        HotkeyRuntimeReleased.clearModule(moduleId);
    }

    @Override
    public void clear() {
        // 解绑：组合键与自带单键一起清（组合键存在时优先展示，只清一个会显得没反应）
        if (chordSupported) ModulePanelRegistry.setPanelChord(moduleId, KeyChord.EMPTY);
        setKeyCode(GLFW.GLFW_KEY_UNKNOWN);
    }
}
