package dev.micx.micxfabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Client-only sprint toggle. Vanilla remains responsible for sprint eligibility and packets. */
public final class ToggleSprintModule implements Module {
    private static final ToggleSprintModule INSTANCE = new ToggleSprintModule();
    private static final int DEFAULT_KEY = GLFW.GLFW_KEY_I;
    private boolean enabled;
    private boolean active = true;
    private InputBinding binding = new InputBinding(DEFAULT_KEY);
    private boolean configLoaded;

    private ToggleSprintModule() {
    }

    public static ToggleSprintModule instance() {
        return INSTANCE;
    }

    public boolean active() {
        return active;
    }

    @Override
    public String id() {
        return "toggle_sprint";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public void setEnabled(boolean enabled) {
        loadConfig();
        this.enabled = enabled;
        if (!enabled) {
            active = false;
            releaseSprint(Minecraft.getInstance());
        }
        ModuleStateStore.put(id(), enabled);
    }

    @Override
    public InputBinding primaryBinding() {
        loadConfig();
        return binding;
    }

    @Override
    public void onPrimaryPressed(Minecraft client) {
        if (!active) active = true;
        else active = false;
        if (!active) releaseSprint(client);
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(ChatMessageStyles.notice("ToggleSprint: " + (active ? "ON" : "OFF")));
        }
    }

    @Override
    public void tick(Minecraft client) {
        if (!active || client == null || client.player == null || client.level == null
                || client.gui.screen() != null || client.isPaused()) {
            return;
        }
        KeyMapping sprint = client.options.keySprint;
        if (sprint != null) sprint.setDown(true);
    }

    @Override
    public void resetInput() {
        active = true;
        releaseSprint(Minecraft.getInstance());
    }

    @Override
    public void resetState() {
        active = true;
        releaseSprint(Minecraft.getInstance());
    }

    public int keyCode() {
        loadConfig();
        return binding.code();
    }

    public void setKeyCode(int code) {
        loadConfig();
        binding.setCode(normalize(code));
        saveConfig();
    }

    public void setActive(boolean active) {
        this.active = active;
        if (!active) releaseSprint(Minecraft.getInstance());
    }

    private void releaseSprint(Minecraft client) {
        if (client == null || client.options == null || client.options.keySprint == null) return;
        KeyMapping sprint = client.options.keySprint;
        if (binding.code() >= 0 && binding.code() != GLFW.GLFW_KEY_UNKNOWN) {
            sprint.setDown(GLFW.glfwGetKey(client.getWindow().handle(), binding.code()) == GLFW.GLFW_PRESS);
        } else {
            sprint.setDown(false);
        }
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path current = FabricRuntime.configPath().resolve("toggle-sprint.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_ToggleSprint.cfg");
        Properties properties = ConfigProperties.load(current, legacy);
        binding = new InputBinding(ConfigProperties.integer(properties, "keyCode", DEFAULT_KEY,
                -108, GLFW.GLFW_KEY_LAST));
        active = ConfigProperties.bool(properties, "active", true);
    }

    public void saveConfig() {
        loadConfig();
        Path file = FabricRuntime.configPath().resolve("toggle-sprint.properties");
        Properties properties = new Properties();
        properties.setProperty("keyCode", Integer.toString(binding.code()));
        properties.setProperty("active", Boolean.toString(active));
        try {
            AtomicProperties.store(file, properties, "MICx ToggleSprint configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save ToggleSprint configuration", exception);
        }
    }

    private static int normalize(int code) {
        return code == 0 ? GLFW.GLFW_KEY_UNKNOWN : Math.max(-108, Math.min(GLFW.GLFW_KEY_LAST, code));
    }
}
