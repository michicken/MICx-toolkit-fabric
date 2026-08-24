package dev.micx.micxfabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Native KeyMapping clicker for the Forge 23/234/24/34 modes.
 * It queues hotbar key clicks and never constructs interaction packets itself.
 * Reload recovery uses the same native hotbar and attack mappings.
 */
public final class KeyboardClickerModule implements Module {
    private static final KeyboardClickerModule INSTANCE = new KeyboardClickerModule();
    private static final int DEFAULT_TOGGLE_KEY = InputConstants.KEY_GRAVE;
    private static final int DEFAULT_MODE_KEY = GLFW.GLFW_KEY_V;
    private static final int MIN_INTERVAL = 40;
    private static final int MAX_INTERVAL = 100;
    private static final int[][] MODES = {{}, {1, 2}, {1, 2, 3}, {1, 3}, {2, 3}};
    private static final String[] MODE_NAMES = {"OFF", "23", "234", "24", "34"};
    private static final float VERY_LOW_RATIO = 0.75f;
    private static final long JAM_DETECT_MS = 150L;
    private static final long SKIP_LOG_INTERVAL_MS = 1_000L;
    private static final int DOWN_NORMAL = 0;
    private static final int DOWN_DOWNED = 1;
    private static final int DOWN_PROTECTING = 2;

    private boolean enabled;
    private int modeIndex;
    private int pendingMode = 1;
    private int sequenceIndex;
    private long lastClick;
    private long lastNewGameRoundStartMs;
    private int toggleKey = DEFAULT_TOGGLE_KEY;
    private int modeKey = DEFAULT_MODE_KEY;
    private int clickInterval = 50;
    private boolean rightClickTrigger;
    private boolean configLoaded;
    private Properties config = new Properties();

    private long stuckProtectCooldown;
    private long stuckPauseUntil;
    private JamProtectionSequence pendingProtection;
    private final Map<Integer, Long> slotVeryLowSince = new HashMap<>();
    private final Map<Integer, Integer> slotLastDamage = new HashMap<>();
    private final Map<Integer, Long> slotSkipLogTime = new HashMap<>();

    private int downJamState = DOWN_NORMAL;
    private final int[] preDownStackSize = new int[9];
    private final int[] downJamSlots = new int[8];
    private int downJamCount;
    private int downJamIndex;
    private int downJamStage;
    private long downJamStageTime;
    private boolean downJamClickerWasActive;
    private int downJamPreviousSlot;
    private long downJamCooldownUntil;

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
        lastClick = 0L;
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(ChatMessageStyles.notice("KeyboardClicker: " + modeName()));
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
            lastClick = 0L;
        }
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(ChatMessageStyles.notice("KeyboardClicker mode=" + modeName()));
        }
    }

    @Override
    public void tick(Minecraft client) {
        long now = System.currentTimeMillis();
        if (client == null || client.player == null || client.level == null) {
            resetProtectionState();
            return;
        }
        if (client.gui.screen() != null || client.isPaused()) {
            resetProtectionState();
            return;
        }

        advanceProtectionSequence(client, now);
        advanceDownJamProtection(client, now);
        resetClickerModesOnNewGame();
        checkJamDetection(client, now);
        checkDownedJamDetection(client, now);

        if (pendingProtection != null || downJamState == DOWN_PROTECTING) return;
        if (now < stuckPauseUntil) return;
        if (modeIndex == 0 || (rightClickTrigger && !client.options.keyUse.isDown())) return;
        if (now - lastClick < clickInterval) return;

        int[] sequence = MODES[modeIndex];
        if (sequence.length == 0) return;
        for (int attempts = 0; attempts < sequence.length; attempts++) {
            int hotbarSlot = sequence[(sequenceIndex + attempts) % sequence.length];
            ItemStack item = client.player.getInventory().getItem(hotbarSlot);
            if (!item.isEmpty() && item.getMaxDamage() > 0 && item.getDamageValue() > 0) {
                emitSkipDiagnostic(hotbarSlot, item, now);
                continue;
            }
            queueHotbarSlot(hotbarSlot);
            sequenceIndex = (sequenceIndex + attempts + 1) % sequence.length;
            lastClick = now;
            return;
        }
    }

    private void checkJamDetection(Minecraft client, long now) {
        if (downJamState == DOWN_PROTECTING) return;
        if (modeIndex == 0) {
            slotVeryLowSince.clear();
            slotLastDamage.clear();
            return;
        }
        for (int sequenceSlot : MODES[modeIndex]) {
            if (sequenceSlot < 0 || sequenceSlot > 8) continue;
            ItemStack item = client.player.getInventory().getItem(sequenceSlot);
            if (item.isEmpty() || item.getMaxDamage() <= 0) {
                clearJamSlot(sequenceSlot);
                continue;
            }
            int damage = item.getDamageValue();
            int maxDamage = item.getMaxDamage();
            boolean veryLow = damage >= Math.max(1, (int) (maxDamage * VERY_LOW_RATIO));
            if (!veryLow) {
                clearJamSlot(sequenceSlot);
                continue;
            }
            Integer previousDamage = slotLastDamage.get(sequenceSlot);
            if (previousDamage == null || previousDamage != damage) {
                slotLastDamage.put(sequenceSlot, damage);
                slotVeryLowSince.put(sequenceSlot, now);
                continue;
            }
            long since = slotVeryLowSince.getOrDefault(sequenceSlot, now);
            if (now - since < JAM_DETECT_MS) continue;
            clearJamSlot(sequenceSlot);
            if (now < stuckProtectCooldown || pendingProtection != null) continue;
            stuckProtectCooldown = now + JamProtectionRules.PROTECT_COOLDOWN_MS;
            stuckPauseUntil = now + JamProtectionRules.STUCK_PAUSE_MS;
            pendingProtection = new JamProtectionSequence(sequenceSlot,
                    client.player.getInventory().getSelectedSlot());
            MicxFabric.LOGGER.debug("KeyboardClicker jam protection triggered for slot {}", sequenceSlot);
        }
    }

    private void advanceProtectionSequence(Minecraft client, long now) {
        JamProtectionSequence sequence = pendingProtection;
        if (sequence == null) return;
        JamProtectionSequence.Action action = sequence.advance(selectedSlot(client), now);
        switch (action.kind) {
            case SELECT -> queueHotbarSlot(action.slot);
            case LEFT_CLICK -> queueLeftClick();
            case RESTORE -> queueHotbarSlot(action.slot);
            case CANCEL -> cancelProtection("manual_slot_change_or_timeout");
            case COMPLETE -> pendingProtection = null;
            case NONE -> {
            }
        }
    }

    private void checkDownedJamDetection(Minecraft client, long now) {
        if (pendingProtection != null || now < downJamCooldownUntil) return;
        int[] counts = new int[9];
        boolean[] weaponSlots = new boolean[9];
        boolean allEmpty = true;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = client.player.getInventory().getItem(slot);
            counts[slot] = item.isEmpty() ? 0 : item.getCount();
            weaponSlots[slot] = !item.isEmpty() && item.getMaxDamage() > 0;
            if (counts[slot] > 0) allEmpty = false;
        }
        if (downJamState == DOWN_NORMAL) {
            if (allEmpty) {
                downJamState = DOWN_DOWNED;
            } else {
                for (int slot = 1; slot < 9; slot++) {
                    preDownStackSize[slot] = weaponSlots[slot] ? counts[slot] : 0;
                }
            }
            return;
        }
        if (downJamState != DOWN_DOWNED || allEmpty) return;
        int[] recovered = JamProtectionRules.findRecoveredGunSlots(preDownStackSize, counts, weaponSlots);
        if (recovered.length == 0) {
            downJamState = DOWN_NORMAL;
            return;
        }
        System.arraycopy(recovered, 0, downJamSlots, 0, recovered.length);
        downJamCount = recovered.length;
        downJamIndex = 0;
        downJamStage = 0;
        downJamStageTime = now;
        downJamClickerWasActive = modeIndex > 0;
        downJamPreviousSlot = selectedSlot(client);
        downJamState = DOWN_PROTECTING;
        MicxFabric.LOGGER.debug("KeyboardClicker downed jam protection triggered for {} slots", downJamCount);
    }

    private void advanceDownJamProtection(Minecraft client, long now) {
        if (downJamState != DOWN_PROTECTING) return;
        if (downJamIndex >= downJamCount) {
            finishDownJamProtection(client, now);
            return;
        }
        int targetSlot = downJamSlots[downJamIndex];
        if (downJamStage == 0) {
            queueHotbarSlot(targetSlot);
            downJamStage = 1;
            downJamStageTime = now;
        } else if (downJamStage == 1) {
            if (selectedSlot(client) == targetSlot) {
                queueLeftClick();
                downJamStage = 2;
                downJamStageTime = now;
            } else if (now - downJamStageTime >= JamProtectionRules.DOWN_SLOT_WAIT_MS) {
                downJamIndex++;
                downJamStage = 0;
            }
        } else if (now - downJamStageTime >= JamProtectionRules.DOWN_GUN_WINDOW_MS) {
            downJamIndex++;
            downJamStage = 0;
        }
    }

    private void finishDownJamProtection(Minecraft client, long now) {
        int round = ZombiesTracker.instance().round();
        if (JamProtectionRules.isSpecialRound(round)) {
            queueHotbarSlot(0);
        } else if (!downJamClickerWasActive) {
            queueHotbarSlot(downJamPreviousSlot);
        }
        downJamState = DOWN_NORMAL;
        downJamCount = 0;
        downJamIndex = 0;
        downJamStage = 0;
        downJamClickerWasActive = false;
        downJamCooldownUntil = now + JamProtectionRules.DOWN_COOLDOWN_MS;
    }

    private void resetClickerModesOnNewGame() {
        ZombiesTracker tracker = ZombiesTracker.instance();
        if (!tracker.isInAlienArcadium() || tracker.round() != 1 || tracker.roundStartMs() <= 0L
                || tracker.roundStartMs() == lastNewGameRoundStartMs) return;
        lastNewGameRoundStartMs = tracker.roundStartMs();
        loadConfig();
        pendingMode = enabledModes().stream().findFirst().orElse(1);
        modeIndex = 0;
        sequenceIndex = 0;
        lastClick = 0L;
        resetProtectionState();
    }

    private void emitSkipDiagnostic(int slot, ItemStack item, long now) {
        long last = slotSkipLogTime.getOrDefault(slot, 0L);
        if (now - last < SKIP_LOG_INTERVAL_MS) return;
        slotSkipLogTime.put(slot, now);
        MicxFabric.LOGGER.debug("KeyboardClicker skipped damaged hotbar slot {} ({}/{})",
                slot, item.getDamageValue(), item.getMaxDamage());
    }

    private void clearJamSlot(int slot) {
        slotVeryLowSince.remove(slot);
        slotLastDamage.remove(slot);
    }

    private void cancelProtection(String reason) {
        if (pendingProtection != null) {
            MicxFabric.LOGGER.debug("KeyboardClicker protection cancelled: {}", reason);
        }
        pendingProtection = null;
    }

    private void resetProtectionState() {
        pendingProtection = null;
        stuckProtectCooldown = 0L;
        stuckPauseUntil = 0L;
        slotVeryLowSince.clear();
        slotLastDamage.clear();
        slotSkipLogTime.clear();
        resetDownJamState();
    }

    private void resetDownJamState() {
        downJamState = DOWN_NORMAL;
        downJamCount = 0;
        downJamIndex = 0;
        downJamStage = 0;
        downJamStageTime = 0L;
        downJamClickerWasActive = false;
        downJamPreviousSlot = 0;
        downJamCooldownUntil = 0L;
        for (int i = 0; i < preDownStackSize.length; i++) preDownStackSize[i] = 0;
    }

    private int selectedSlot(Minecraft client) {
        return client.player.getInventory().getSelectedSlot();
    }

    private void queueHotbarSlot(int slot) {
        if (slot < 0 || slot > 8) return;
        KeyMapping.click(InputConstants.getKey(new KeyEvent(GLFW.GLFW_KEY_1 + slot, 0, 0)));
    }

    private void queueLeftClick() {
        KeyMapping.click(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_LEFT));
    }

    @Override
    public void resetInput() {
        modeIndex = 0;
        sequenceIndex = 0;
        lastClick = 0L;
        resetProtectionState();
    }

    @Override
    public void resetState() {
        resetInput();
        lastNewGameRoundStartMs = 0L;
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

    public boolean isMode23() { return enabledModes().contains(1); }
    public boolean isMode234() { return enabledModes().contains(2); }
    public boolean isMode24() { return enabledModes().contains(3); }
    public boolean isMode34() { return enabledModes().contains(4); }

    public void setMode23(boolean value) { setModeEnabled(1, value); }
    public void setMode234(boolean value) { setModeEnabled(2, value); }
    public void setMode24(boolean value) { setModeEnabled(3, value); }
    public void setMode34(boolean value) { setModeEnabled(4, value); }

    private void setModeEnabled(int mode, boolean value) {
        loadConfig();
        List<Integer> modes = enabledModes();
        if (!value && modes.size() <= 1 && modes.contains(mode)) return;
        config.setProperty("mode" + MODE_NAMES[mode], Boolean.toString(value));
        if (!value && modeIndex == mode) {
            pendingMode = enabledModes().stream().findFirst().orElse(1);
            modeIndex = pendingMode;
            sequenceIndex = 0;
        }
        if (value && modeIndex == 0) pendingMode = mode;
        saveConfig();
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
