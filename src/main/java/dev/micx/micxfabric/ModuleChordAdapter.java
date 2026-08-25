package dev.micx.micxfabric;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Bridges a module primary combo chord (1–3 keys) into the panel's editable keybind contract. */
public final class ModuleChordAdapter implements ModuleKeybind {
    private final String moduleId;
    private final Supplier<int[]> getter;
    private final Consumer<int[]> setter;
    private String description;

    public ModuleChordAdapter(
            String moduleId,
            String description,
            Supplier<int[]> getter,
            Consumer<int[]> setter
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
        int[] codes = getter.get();
        return KeyChord.isEmpty(codes) ? -1 : KeyChord.primary(codes);
    }

    @Override
    public String keyLabel() {
        return KeyChord.display(getter.get());
    }

    @Override
    public boolean supportsChord() {
        return true;
    }

    @Override
    public void setChordCodes(int[] codes) {
        int[] next = KeyChord.normalize(codes);
        setter.accept(next);
        HotkeyRuntime.clearModule(moduleId);
    }

    @Override
    public void setKeyCode(int keyCode) {
        setChordCodes(KeyChord.single(keyCode == 0 ? 0 : keyCode));
    }

    @Override
    public void clear() {
        setChordCodes(KeyChord.EMPTY);
    }
}
