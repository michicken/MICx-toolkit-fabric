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

    /** 是否支持多键组合捕获（chord）；false 时面板走单键即时提交。 */
    default boolean supportsChord() {
        return false;
    }

    /** 组合捕获提交（1–3 键，空数组 = 解除绑定）。 */
    default void setChordCodes(int[] codes) {
        throw new UnsupportedOperationException("Keybind is not writable");
    }

    void clear();
}
