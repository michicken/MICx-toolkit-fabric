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
    private String description;

    public ModuleKeybindAdapter(
            String moduleId,
            String description,
            Supplier<InputBinding> getter,
            IntConsumer setter
    ) {
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        this.description = Objects.requireNonNull(description, "description");
        this.getter = Objects.requireNonNull(getter, "getter");
        this.setter = Objects.requireNonNull(setter, "setter");
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
        InputBinding binding = getter.get();
        if (binding == null || binding.isUnbound()) return "未绑定";
        return KeyChord.keyName(binding.code());
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
        setKeyCode(GLFW.GLFW_KEY_UNKNOWN);
    }
}
