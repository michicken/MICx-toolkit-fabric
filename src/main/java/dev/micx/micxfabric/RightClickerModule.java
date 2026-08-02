package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/** Releases vanilla's four-tick use delay while the native use key is held. */
public final class RightClickerModule implements Module {
    private static final RightClickerModule INSTANCE = new RightClickerModule();
    private static final int DEFAULT_KEY = -98;
    private boolean enabled;
    private boolean active = true;
    private InputBinding binding = new InputBinding(DEFAULT_KEY);
    private boolean configLoaded;

    private RightClickerModule() {
    }

    public static RightClickerModule instance() {
        return INSTANCE;
    }

    public static boolean isActive() {
        return INSTANCE.enabled && INSTANCE.active;
    }

    @Override
    public String id() {
        return "right_clicker";
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
        boolean wasEnabled = this.enabled;
        this.enabled = enabled;
        if (enabled && !wasEnabled) active = true;
        if (!enabled) active = false;
        ModuleStateStore.put(id(), enabled);
    }

    @Override
    public InputBinding primaryBinding() {
        loadConfig();
        return binding;
    }

    @Override
    public void onPrimaryPressed(Minecraft client, boolean newlyEnabled) {
        if (newlyEnabled) active = true;
        else active = !active;
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(ChatMessageStyles.notice("RightClicker: " + (active ? "ON" : "OFF")));
        }
    }

    @Override
    public void resetInput() {
        active = false;
    }

    @Override
    public void resetState() {
        active = true;
    }

    public boolean active() {
        return active;
    }

    public int keyCode() {
        loadConfig();
        return binding.code();
    }

    public void setKeyCode(int code) {
        loadConfig();
        binding.setCode(code == 0 ? GLFW.GLFW_KEY_UNKNOWN : Math.max(-108, Math.min(GLFW.GLFW_KEY_LAST, code)));
        saveConfig();
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path current = FabricRuntime.configPath().resolve("right-clicker.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_RightClicker.cfg");
        Properties properties = ConfigProperties.load(current, legacy);
        binding = new InputBinding(ConfigProperties.integer(properties, "toggleKeyCode", DEFAULT_KEY, -108, GLFW.GLFW_KEY_LAST));
        active = ConfigProperties.bool(properties, "active", true);
    }

    private void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("toggleKeyCode", Integer.toString(binding.code()));
        properties.setProperty("active", Boolean.toString(active));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("right-clicker.properties"), properties,
                    "MICx RightClicker configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save RightClicker configuration", exception);
        }
    }
}
