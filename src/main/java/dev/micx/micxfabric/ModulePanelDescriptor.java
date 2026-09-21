package dev.micx.micxfabric;

import net.minecraft.client.gui.screens.Screen;

import java.util.Objects;
import java.util.function.Function;

/**
 * A module entry shown by the Fabric control panel.
 *
 * <p>中文名与说明都存 {@link UiText.Txt} 本体而不是已解析的字符串：描述符在
 * {@code ModulePanelRegistry} 的静态块里一次性构造，那时候还取不到「当前显示哪一版文案」，
 * 提前解析会把选择冻结在类加载的瞬间。渲染时再 {@link UiText#of} 解析即可实时跟随顶部开关。
 */
public final class ModulePanelDescriptor {
    private final String id;
    private final String displayName;
    private final UiText.Txt chineseName;
    private final String group;
    private final int order;
    private final UiText.Txt description;
    private final Module module;
    private final Function<Screen, Screen> configScreenFactory;
    private final ModuleKeybind keybind;
    private final boolean migrated;
    private final boolean blocked;

    /** Creates a descriptor for a real, controllable Fabric module. */
    public ModulePanelDescriptor(
            String id,
            String displayName,
            UiText.Txt chineseName,
            String group,
            int order,
            UiText.Txt description,
            Module module,
            Function<Screen, Screen> configScreenFactory,
            ModuleKeybind keybind) {
        this(id, displayName, chineseName, group, order, description, module,
                configScreenFactory, keybind, true, false);
    }

    /** Creates a descriptor whose runtime availability is explicit in the panel. */
    public ModulePanelDescriptor(
            String id,
            String displayName,
            UiText.Txt chineseName,
            String group,
            int order,
            UiText.Txt description,
            Module module,
            Function<Screen, Screen> configScreenFactory,
            ModuleKeybind keybind,
            boolean migrated,
            boolean blocked) {
        this.id = requireText(id, "id");
        this.displayName = requireText(displayName, "displayName");
        this.chineseName = requireText(chineseName, "chineseName");
        this.group = requireText(group, "group");
        this.order = order;
        this.description = requireText(description, "description");
        this.module = Objects.requireNonNull(module, "module");
        this.configScreenFactory = configScreenFactory;
        this.keybind = keybind;
        this.migrated = migrated;
        this.blocked = blocked;
        if (!id.equals(module.id())) {
            throw new IllegalArgumentException("Descriptor id does not match module id: " + id);
        }
        if (!migrated && configScreenFactory != null) {
            throw new IllegalArgumentException("Unmigrated descriptor cannot expose a configuration screen: " + id);
        }
        if (blocked && migrated) {
            throw new IllegalArgumentException("Blocked descriptor cannot be marked migrated: " + id);
        }
    }

    public String id() {
        return id;
    }

    /** 模块的英文短名（面板里作小字副标）。 */
    public String displayName() {
        return displayName;
    }

    /** 模块的中文名（面板里作主标题），随「新版 / 旧版文案」开关切换。 */
    public String chineseName() {
        return UiText.of(chineseName);
    }

    public String group() {
        return group;
    }

    public int order() {
        return order;
    }

    /** 模块介绍，随「新版 / 旧版文案」开关切换。 */
    public String description() {
        return UiText.of(description);
    }

    public Module module() {
        return module;
    }

    public boolean isMigrated() {
        return migrated;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public boolean isControllable() {
        return migrated && !blocked;
    }

    public String statusLabel() {
        if (blocked) return "BLOCKED";
        return migrated ? (module.enabled() ? "LIVE" : "OFF") : "NOT MIGRATED";
    }

    public boolean hasConfigScreen() {
        return configScreenFactory != null && isControllable();
    }

    public Screen createConfigScreen(Screen parent) {
        if (!hasConfigScreen()) return null;
        return configScreenFactory.apply(parent);
    }

    public boolean hasKeybind() {
        return keybind != null && isControllable();
    }

    public ModuleKeybind keybind() {
        return keybind;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name);
        return value;
    }

    private static UiText.Txt requireText(UiText.Txt value, String name) {
        if (value == null || value.now().isBlank() || value.was().isBlank()) {
            throw new IllegalArgumentException(name);
        }
        return value;
    }
}
