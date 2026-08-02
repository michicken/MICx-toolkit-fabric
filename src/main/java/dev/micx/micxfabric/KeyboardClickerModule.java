package dev.micx.micxfabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Native KeyMapping clicker for the Forge 23/234/24/34 modes.
 * It queues hotbar key clicks and never constructs interaction packets itself.
 */
public final class KeyboardClickerModule implements Module {
    private static final KeyboardClickerModule INSTANCE = new KeyboardClickerModule();
    private static final int DEFAULT_TOGGLE_KEY = InputConstants.KEY_GRAVE;
    private static final int DEFAULT_MODE_KEY = GLFW.GLFW_KEY_V;
    private static final int MIN_INTERVAL = 40;
    private static final int MAX_INTERVAL = 100;
    private static final int[][] MODES = {{}, {1, 2}, {1, 2, 3}, {1, 3}, {2, 3}};
    private static final String[] MODE_NAMES = {"OFF", "23", "234", "24", "34"};

    private boolean enabled;
    private int modeIndex;
    private int pendingMode = 1;
    private int sequenceIndex;
    private long lastClick;
    private int toggleKey = DEFAULT_TOGGLE_KEY;
    private int modeKey = DEFAULT_MODE_KEY;
    private int clickInterval = 50;
    private boolean rightClickTrigger;
    private boolean configLoaded;

    private KeyboardClickerModule() {
    }

    public static KeyboardClickerModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "keyboard_clicker";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public void setEnabled(boolean enabled) {
        loadConfig();
        this.enabled = enabled;
        if (!enabled) resetInput();
        ModuleStateStore.put(id(), enabled);
    }

    @Override
    public InputBinding primaryBinding() {
        loadConfig();
        return new InputBinding(toggleKey);
    }

    @Override
    public InputBinding secondaryBinding() {
        loadConfig();
        return new InputBinding(modeKey);
    }

    @Override
    public void onPrimaryPressed(Minecraft client, boolean newlyEnabled) {
        if (modeIndex == 0) modeIndex = pendingMode;
        else modeIndex = 0;
        sequenceIndex = 0;
        lastClick = 0;
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal("KeyboardClicker: " + modeName()));
        }
    }

    @Override
    public void onSecondaryPressed(Minecraft client) {
        List<Integer> modes = enabledModes();
        if (modes.isEmpty()) return;
        int current = modeIndex == 0 ? pendingMode : modeIndex;
        int index = modes.indexOf(current);
        int next = modes.get((index + 1 + modes.size()) % modes.size());
        pendingMode = next;
        if (modeIndex != 0) {
            modeIndex = next;
            sequenceIndex = 0;
            lastClick = 0;
        }
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal("KeyboardClicker mode=" + modeName()));
        }
    }

    @Override
    public void tick(Minecraft client) {
        if (modeIndex == 0 || client == null || client.player == null || client.level == null
                || client.gui.screen() != null || client.isPaused()) return;
        if (rightClickTrigger && !client.options.keyUse.isDown()) return;
        long now = System.currentTimeMillis();
        if (now - lastClick < clickInterval) return;
        int[] sequence = MODES[modeIndex];
        if (sequence.length == 0) return;
        for (int attempts = 0; attempts < sequence.length; attempts++) {
            int sequenceSlot = sequence[(sequenceIndex + attempts) % sequence.length];
            int hotbarSlot = sequenceSlot;
            ItemStack item = client.player.getInventory().getItem(hotbarSlot);
            if (!item.isEmpty() && item.getMaxDamage() > 0 && item.getDamageValue() > 0) continue;
            KeyMapping.click(InputConstants.getKey(new net.minecraft.client.input.KeyEvent(
                    GLFW.GLFW_KEY_1 + hotbarSlot, 0, 0)));
            sequenceIndex = (sequenceIndex + attempts + 1) % sequence.length;
            lastClick = now;
            return;
        }
    }

    @Override
    public void resetInput() {
        modeIndex = 0;
        sequenceIndex = 0;
        lastClick = 0;
    }

    public int modeIndex() {
        return modeIndex;
    }

    public String modeName() {
        int index = modeIndex == 0 ? pendingMode : modeIndex;
        return index >= 0 && index < MODE_NAMES.length ? MODE_NAMES[index] : "OFF";
    }

    public int toggleKey() {
        loadConfig();
        return toggleKey;
    }

    public int modeKey() {
        loadConfig();
        return modeKey;
    }

    public int clickInterval() {
        loadConfig();
        return clickInterval;
    }

    public boolean rightClickTrigger() {
        loadConfig();
        return rightClickTrigger;
    }

    public void setToggleKey(int value) {
        loadConfig();
        toggleKey = normalize(value);
        saveConfig();
    }

    public void setModeKey(int value) {
        loadConfig();
        modeKey = normalize(value);
        saveConfig();
    }

    public void setClickInterval(int value) {
        loadConfig();
        clickInterval = Math.max(MIN_INTERVAL, Math.min(MAX_INTERVAL, value));
        saveConfig();
    }

    public void setRightClickTrigger(boolean value) {
        loadConfig();
        rightClickTrigger = value;
        saveConfig();
    }

    private List<Integer> enabledModes() {
        loadConfig();
        List<Integer> modes = new ArrayList<>();
        for (int index = 1; index < MODES.length; index++) {
            if (ConfigProperties.bool(config, "mode" + MODE_NAMES[index], index <= 2)) modes.add(index);
        }
        return modes;
    }

    private Properties config = new Properties();

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path current = FabricRuntime.configPath().resolve("keyboard-clicker.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_KeyboardClicker.cfg");
        config = ConfigProperties.load(current, legacy);
        toggleKey = ConfigProperties.integer(config, "toggleKey", DEFAULT_TOGGLE_KEY, -108, GLFW.GLFW_KEY_LAST);
        modeKey = ConfigProperties.integer(config, "modeKey", DEFAULT_MODE_KEY, -108, GLFW.GLFW_KEY_LAST);
        clickInterval = ConfigProperties.integer(config, "clickIntervalMs", 50, MIN_INTERVAL, MAX_INTERVAL);
        rightClickTrigger = ConfigProperties.bool(config, "rightClickTrigger", false);
        pendingMode = enabledModesFromConfig().stream().findFirst().orElse(1);
    }

    private List<Integer> enabledModesFromConfig() {
        List<Integer> modes = new ArrayList<>();
        for (int index = 1; index < MODES.length; index++) {
            if (ConfigProperties.bool(config, "mode" + MODE_NAMES[index], index <= 2)) modes.add(index);
        }
        return modes;
    }

    private void saveConfig() {
        Properties properties = new Properties();
        properties.putAll(config);
        properties.setProperty("toggleKey", Integer.toString(toggleKey));
        properties.setProperty("modeKey", Integer.toString(modeKey));
        properties.setProperty("clickIntervalMs", Integer.toString(clickInterval));
        properties.setProperty("rightClickTrigger", Boolean.toString(rightClickTrigger));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("keyboard-clicker.properties"), properties,
                    "MICx KeyboardClicker configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save KeyboardClicker configuration", exception);
        }
        config = properties;
    }

    private static int normalize(int value) {
        return value == 0 ? GLFW.GLFW_KEY_UNKNOWN : Math.max(-108, Math.min(GLFW.GLFW_KEY_LAST, value));
    }
}
