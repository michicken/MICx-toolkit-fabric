package dev.micx.micxfabric;

import net.minecraft.client.gui.screens.Screen;

import java.util.Objects;
import java.util.function.Function;

/** A module entry shown by the Fabric control panel. */
public final class ModulePanelDescriptor {
    private final String id;
    private final String displayName;
    private final String chineseName;
    private final String group;
    private final int order;
    private final String description;
    private final Module module;
    private final Function<Screen, Screen> configScreenFactory;
    private final ModuleKeybind keybind;
    private final boolean migrated;
    private final boolean blocked;

    /** Creates a descriptor for a real, controllable Fabric module. */
    public ModulePanelDescriptor(
            String id,
            String displayName,
            String chineseName,
            String group,
            int order,
            String description,
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
            String chineseName,
            String group,
            int order,
            String description,
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
        this.description = Objects.requireNonNull(description, "description");
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

    public String displayName() {
        return displayName;
    }

    public String chineseName() {
        return chineseName;
    }

    public String group() {
        return group;
    }

    public int order() {
        return order;
    }

    public String description() {
        return description;
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
}
