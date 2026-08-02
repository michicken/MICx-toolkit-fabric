package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/** Three-tick native-use state machine copied from the Forge SkillCast timing contract. */
public final class SkillCastModule implements Module {
    private static final SkillCastModule INSTANCE = new SkillCastModule();
    private static final int DEFAULT_KEY = GLFW.GLFW_KEY_F;
    private static final Set<Integer> SPECIAL_ROUNDS = new HashSet<>();
    static {
        for (int round : new int[]{53, 55, 58, 69, 70, 75, 79, 80}) SPECIAL_ROUNDS.add(round);
    }

    private boolean enabled;
    private int keyCode = DEFAULT_KEY;
    private boolean configLoaded;
    private int stage;
    private int previousSlot = -1;
    private int restoreSlot = -1;
    private boolean specialRound;
    private long lastFire;

    private SkillCastModule() {
    }

    public static SkillCastModule instance() {
        return INSTANCE;
    }

    public static boolean isSpecialRound(int round) {
        if (round <= 0) return false;
        if (SPECIAL_ROUNDS.contains(round)) return true;
        if (round > 80) {
            int last = round % 10;
            return last == 0 || last == 5 || last == 9;
        }
        return false;
    }

    @Override
    public String id() {
        return "skill_cast";
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
        if (!enabled) resetInput();
        ModuleStateStore.put(id(), enabled);
    }

    @Override
    public InputBinding primaryBinding() {
        loadConfig();
        return new InputBinding(keyCode);
    }

    @Override
    public void onPrimaryPressed(Minecraft client, boolean newlyEnabled) {
        if (newlyEnabled || client == null || client.player == null || client.level == null
                || client.gui.screen() != null || client.isPaused() || stage != 0) return;
        long now = System.currentTimeMillis();
        if (now - lastFire < 100L) return;
        previousSlot = client.player.getInventory().getSelectedSlot();
        specialRound = isSpecialRound(0);
        restoreSlot = specialRound ? 0 : previousSlot;
        stage = 1;
        lastFire = now;
    }

    @Override
    public void tick(Minecraft client) {
        if (stage == 0 || client == null || client.player == null || client.level == null) return;
        if (client.gui.screen() != null || client.isPaused()) {
            resetInput();
            return;
        }
        if (stage == 1) {
            client.player.getInventory().setSelectedSlot(4);
            if (client.gameMode != null) client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
            stage = 2;
            return;
        }
        if (stage == 2) {
            if (client.gameMode != null) client.gameMode.releaseUsingItem(client.player);
            stage = 3;
            return;
        }
        if (stage == 3) {
            if (restoreSlot >= 0 && restoreSlot < 9) client.player.getInventory().setSelectedSlot(restoreSlot);
            stage = 0;
            client.player.sendSystemMessage(ChatMessageStyles.notice("已触发技能释放"));
        }
    }

    @Override
    public void resetInput() {
        Minecraft client = Minecraft.getInstance();
        if (stage != 0 && client != null && client.player != null
                && restoreSlot >= 0 && restoreSlot < 9) {
            client.player.getInventory().setSelectedSlot(restoreSlot);
        }
        stage = 0;
        previousSlot = -1;
        restoreSlot = -1;
        specialRound = false;
        lastFire = 0L;
    }

    @Override
    public void resetState() {
        resetInput();
    }

    public int keyCode() {
        loadConfig();
        return keyCode;
    }

    public void setKeyCode(int keyCode) {
        loadConfig();
        this.keyCode = keyCode == 0 ? GLFW.GLFW_KEY_UNKNOWN : Math.max(-108, Math.min(GLFW.GLFW_KEY_LAST, keyCode));
        saveConfig();
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path current = FabricRuntime.configPath().resolve("skill-cast.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_SkillCast.cfg");
        Properties properties = ConfigProperties.load(current, legacy);
        keyCode = ConfigProperties.integer(properties, "keyCode", DEFAULT_KEY, -108, GLFW.GLFW_KEY_LAST);
    }

    private void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("keyCode", Integer.toString(keyCode));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("skill-cast.properties"), properties,
                    "MICx SkillCast configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save SkillCast configuration", exception);
        }
    }
}
