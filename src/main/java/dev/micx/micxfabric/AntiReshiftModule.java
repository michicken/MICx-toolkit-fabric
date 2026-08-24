package dev.micx.micxfabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * AntiReshift（Type B-only rescue guard）— Forge AutoReShiftTypeBExperimentModule 的移植。
 *
 * <p>只保护一次已经在进行的本地救援不被误松 Shift 打断：不做自动重按（Type A）、
 * 不调 setSneaking、不碰网络包。唯一主动作是救援有效期间把物理松键事件之后
 * 的本地 KeyBinding 状态恢复为按住。</p>
 *
 * <p>26.2 无 LWJGL2 输入队列：改为 END_CLIENT_TICK 里轮询物理按键状态，
 * 检测"上一 tick 物理按下、本 tick 松开"作为释放事件；本地保持用
 * {@link KeyMapping#setDown(boolean)}。默认关闭。</p>
 */
public final class AntiReshiftModule implements Module {
    private static final AntiReshiftModule INSTANCE = new AntiReshiftModule();
    /** 自己救起队友后立即解除防松，交还玩家。 */
    private static final java.util.regex.Pattern YOU_REVIVED =
            java.util.regex.Pattern.compile("You revived (\\w{1,16})");
    private static final long REVIVED_COOLDOWN_MS = 1500L;

    private int duoStartRound = AutoReshiftTypeBRules.DUO_START_ROUND;
    private int nonDuoStartRound = AutoReshiftTypeBRules.NON_DUO_START_ROUND;
    private float lowHealthHp = AutoReshiftTypeBRules.LOW_HEALTH_BYPASS_HP;

    private boolean enabled;
    private boolean guardActive;
    private String guardTarget;
    private long lastEligibleAt;
    private long revivedBypassUntil;
    private boolean lastPhysicalDown;
    private boolean chatHooked;
    private boolean configLoaded;

    private AntiReshiftModule() {
    }

    public static AntiReshiftModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "anti_reshift";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean value) {
        loadConfig();
        this.enabled = value;
        if (!value) deactivateGuard("disabled");
        hookChat();
        ModuleStateStore.put(id(), value);
    }

    @Override
    public void resetState() {
        deactivateGuard("reset");
        lastEligibleAt = 0L;
    }

    private synchronized void hookChat() {
        if (chatHooked) return;
        chatHooked = true;
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!enabled || message == null) return;
            if (YOU_REVIVED.matcher(message.getString()).find()) {
                revivedBypassUntil = System.currentTimeMillis() + REVIVED_COOLDOWN_MS;
                if (guardActive) deactivateGuard("revived");
            }
        });
    }

    @Override
    public void tick(Minecraft client) {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        if (client == null || client.player == null || client.level == null || client.gui.screen() != null) {
            deactivateGuard("invalid_context");
            return;
        }
        ZombiesTracker tracker = ZombiesTracker.instance();
        boolean duo = playerCount(tracker) == 2;
        if (AutoReshiftTypeBRules.lowHealthBypass(client.player.getHealth(), lowHealthHp, duo)) {
            bypassForLowHealth(client);
            return;
        }
        if (!AutoReshiftTypeBRules.roundGate(tracker.round(), duo, duoStartRound, nonDuoStartRound)
                || now < revivedBypassUntil) {
            // 门槛丢失时已激活的 guard 必须仍能走解除路径，否则会永久黏住 Shift。
            if (guardActive && !AutoReshiftTypeBRules.keepGuardDuringGrace(true, lastEligibleAt, now)) {
                deactivateGuard("gate_lost");
            }
            lastPhysicalDown = isSneakPhysicallyDown(client);
            return;
        }

        ZombiesEventState.RescueAction snapshot = freshRescueSnapshot(client, tracker, now);
        boolean rescueEligible = snapshot != null;
        boolean physicalNow = isSneakPhysicallyDown(client);
        // 释放检测：上一 tick 物理按下、本 tick 松开 → 立即恢复本地按住
        boolean releaseEdge = lastPhysicalDown && !physicalNow;
        lastPhysicalDown = physicalNow;

        boolean eligible = rescueEligible && (guardActive || physicalNow);
        if (eligible) {
            lastEligibleAt = now;
            if (!guardActive) {
                guardActive = true;
                guardTarget = snapshot.target();
            } else if (guardTarget == null || !guardTarget.equals(snapshot.target())) {
                guardTarget = snapshot.target();
            }
        } else if (guardActive && releaseEdge && snapshot != null) {
            restoreLocalHold(client);
        }
        if (guardActive && !AutoReshiftTypeBRules.keepGuardDuringGrace(true, lastEligibleAt, now)) {
            deactivateGuard("rescue_ended");
        }
    }

    /** 当前时刻是否有服务端已确认、且仍满足所有前置条件的本地救援快照。 */
    private ZombiesEventState.RescueAction freshRescueSnapshot(Minecraft client,
                                                               ZombiesTracker tracker, long now) {
        String self = client.player.getName().getString();
        ZombiesEventState.RescueAction snapshot = tracker.eventState().localRescueAction(now);
        if (snapshot == null) return null;
        boolean duo = playerCount(tracker) == 2;
        if (!AutoReshiftTypeBRules.shouldGuard(
                tracker.isInAlienArcadium(),
                "down".equals(tracker.playerStatus(self)),
                "dead".equals(tracker.playerStatus(self)),
                snapshot.target(),
                "down".equals(tracker.playerStatus(snapshot.target())),
                snapshot.modeMs(),
                snapshot.observedAt(),
                now)) {
            return null;
        }
        return snapshot;
    }

    private int playerCount(ZombiesTracker tracker) {
        int size = tracker.frame().playerStatuses().size();
        return size > 0 ? size : 1;
    }

    private void bypassForLowHealth(Minecraft client) {
        if (guardActive) {
            deactivateGuard("low_health");
            if (client.player != null) {
                client.player.sendSystemMessage(
                        ChatMessageStyles.muted("生命值低于 " + (int) lowHealthHp + " HP，防松已暂停，Shift 松键放行"));
            }
        }
    }

    private void deactivateGuard(String reason) {
        if (!guardActive) return;
        guardActive = false;
        guardTarget = null;
        lastEligibleAt = 0L;
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            // 归还真实物理状态，避免松开后本地仍显示按住
            try {
                boolean physical = isSneakPhysicallyDown(client);
                if (client.options.keyShift.isDown() != physical) {
                    client.options.keyShift.setDown(physical);
                }
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void restoreLocalHold(Minecraft client) {
        try {
            if (!client.options.keyShift.isDown()) {
                client.options.keyShift.setDown(true);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static boolean isSneakPhysicallyDown(Minecraft client) {
        try {
            InputConstants.Key key = client.options.keyShift.getDefaultKey();
            long window = client.getWindow().handle();
            if (key.getType() == InputConstants.Type.MOUSE) {
                return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            }
            if (key.getType() == InputConstants.Type.KEYSYM) {
                return GLFW.glfwGetKey(window, key.getValue()) == GLFW.GLFW_PRESS;
            }
            return client.options.keyShift.isDown();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /* ---- 配置 ---- */

    public int getDuoStartRound() {
        loadConfig();
        return duoStartRound;
    }

    public int getNonDuoStartRound() {
        loadConfig();
        return nonDuoStartRound;
    }

    public float getLowHealthHp() {
        loadConfig();
        return lowHealthHp;
    }

    public void setDuoStartRound(int v) {
        duoStartRound = Math.max(1, Math.min(100, v));
        saveConfig();
    }

    public void setNonDuoStartRound(int v) {
        nonDuoStartRound = Math.max(1, Math.min(100, v));
        saveConfig();
    }

    public void setLowHealthHp(float v) {
        lowHealthHp = Math.max(1.0f, Math.min(20.0f, v));
        saveConfig();
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path current = FabricRuntime.configPath().resolve("anti-reshift.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_AutoReShiftTypeB.cfg");
        Properties properties = ConfigProperties.load(current, legacy);
        duoStartRound = ConfigProperties.integer(properties, "duoStartRound",
                AutoReshiftTypeBRules.DUO_START_ROUND, 1, 100);
        nonDuoStartRound = ConfigProperties.integer(properties, "nonDuoStartRound",
                AutoReshiftTypeBRules.NON_DUO_START_ROUND, 1, 100);
        lowHealthHp = (float) ConfigProperties.real(properties, "lowHealthHp",
                AutoReshiftTypeBRules.LOW_HEALTH_BYPASS_HP, 1.0, 20.0);
    }

    private void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("duoStartRound", Integer.toString(duoStartRound));
        properties.setProperty("nonDuoStartRound", Integer.toString(nonDuoStartRound));
        properties.setProperty("lowHealthHp", Float.toString(lowHealthHp));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("anti-reshift.properties"), properties,
                    "MICx AntiReshift configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save AntiReshift configuration", exception);
        }
    }

    /** 面板/调试用。 */
    public boolean isGuardActiveForTest() {
        return guardActive;
    }

    public String guardTargetForTest() {
        return guardTarget;
    }

}
