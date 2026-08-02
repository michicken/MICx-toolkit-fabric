package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Sends configured messages once on release of their keyboard or mouse binding. */
public final class AutoTextModule implements Module {
    private static final AutoTextModule INSTANCE = new AutoTextModule();
    private final List<Binding> bindings = new ArrayList<>();
    private boolean enabled;
    private boolean configLoaded;

    private AutoTextModule() {
    }

    public static AutoTextModule instance() {
        return INSTANCE;
    }

    public record Binding(int code, String text) {
    }

    @Override
    public String id() {
        return "auto_text";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        loadConfig();
        this.enabled = enabled;
        ModuleStateStore.put(id(), enabled);
    }

    @Override
    public void tick(Minecraft client) {
        loadConfig();
        if (client == null || client.player == null || client.level == null || client.gui.screen() != null) return;
        for (Binding binding : bindings) {
            if (binding.code() == 0) continue;
            InputBinding input = new InputBinding(binding.code());
            if (HotkeyRuntimeReleased.released(id() + ":" + binding.code(), input, client)) {
                if (!binding.text().isBlank()) client.player.connection.sendChat(binding.text());
            }
        }
    }

    public List<Binding> bindings() {
        loadConfig();
        return List.copyOf(bindings);
    }

    public void replaceBindings(List<Binding> next) {
        bindings.clear();
        if (next != null) {
            for (Binding binding : next) {
                if (binding != null && binding.code() != 0 && binding.text() != null && !binding.text().isBlank()) {
                    bindings.add(new Binding(binding.code(), binding.text()));
                }
            }
        }
        saveConfig();
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path current = FabricRuntime.configPath().resolve("auto-text.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_AutoText.cfg");
        Properties properties = ConfigProperties.load(current, legacy);
        int count = ConfigProperties.integer(properties, "binding.count", 0, 0, 64);
        for (int i = 0; i < count; i++) {
            int code = ConfigProperties.integer(properties, "binding." + i + ".key", 0, -108, 512);
            String text = properties.getProperty("binding." + i + ".text", "");
            if (code != 0 && !text.isBlank()) bindings.add(new Binding(code, text));
        }
    }

    private void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("binding.count", Integer.toString(bindings.size()));
        for (int i = 0; i < bindings.size(); i++) {
            Binding binding = bindings.get(i);
            properties.setProperty("binding." + i + ".key", Integer.toString(binding.code()));
            properties.setProperty("binding." + i + ".text", binding.text());
        }
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("auto-text.properties"), properties,
                    "MICx AutoText bindings");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save AutoText configuration", exception);
        }
    }
}
