package dev.micx.micxfabric;

/** Public keybind contract used by the panel; -1 means unbound. */
public interface ModuleKeybind {
    String description();

    void setDescription(String description);

    default int keyCode() {
        return -1;
    }

    default String keyLabel() {
        return keyCode() < 0 ? "未绑定" : Integer.toString(keyCode());
    }

    default void setKeyCode(int keyCode) {
        throw new UnsupportedOperationException("Keybind is not writable");
    }

    void clear();
}
