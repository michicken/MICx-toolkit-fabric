package dev.micx.micxfabric;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * AntiAXE（Forge 移植）—— 保护 unwanted The Puncher 领取窗。
 *
 * <p>聊天出现 "You found The Puncher in the Lucky Chest!" 时武装 10.5s 拦截窗：
 * 期间准星射线命中 Lucky Chest 领取区则拦截本地右键（Mixin startUseItem），
 * 防止误领 Puncher。领到任何其他物品自动解除。默认关闭。</p>
 */
public final class AntiAxeModule implements Module {
    private static final AntiAxeModule INSTANCE = new AntiAxeModule();

    private boolean enabled;
    private boolean chatHooked;
    private int hudDx;
    private int hudDy = 0;
    private boolean configLoaded;

    private AntiAxeModule() {
    }

    public static AntiAxeModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "anti_axe";
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
        hookChat();
        AntiAxeGuard.setEnabled(value);
        if (!value) AntiAxeAaContext.clear();
        ModuleStateStore.put(id(), value);
    }

    private synchronized void hookChat() {
        if (chatHooked) return;
        chatHooked = true;
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!enabled || message == null) return;
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.player == null) return;
            if (!AntiAxeAaContext.isInAA(client)) return;
            AntiAxeRules.ChatAction action =
                    AntiAxeRules.classifyChat(message.getString());
            if (action == AntiAxeRules.ChatAction.ARM) {
                AntiAxeGuard.arm(System.currentTimeMillis());
                client.player.sendSystemMessage(ChatMessageStyles.error(
                        "[MICx] AntiAXE armed: The Puncher 领取区右键已锁定 10 秒"));
            } else if (action == AntiAxeRules.ChatAction.DISARM) {
                AntiAxeGuard.disarm("chest_state_changed");
            }
        });
    }

    @Override
    public void tick(Minecraft client) {
        if (!enabled) return;
        AntiAxeAaContext.tick(client);
    }

    /** 中央提示：拦截中放大红字，否则琥珀色待命。 */
    public void drawHud(GuiGraphicsExtractor graphics) {
        if (!enabled || !AntiAxeGuard.isArmed()) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) return;
        if (!AntiAxeAaContext.isInAA(client)) return;
        boolean blocking = AntiAxeGuard.isLookingAtClaimZone(client);
        String text = blocking ? "§c§lAntiAXE §f右键已锁" : "§6AntiAXE §fThe Puncher";
        text += String.format(java.util.Locale.ROOT, " §7%.1fs", AntiAxeGuard.remainingMs() / 1000.0);
        Component component = LegacyText.of(text);
        float scale = blocking ? 1.35f : 1.0f;
        float scaleX = HudLayoutRegistry.scaleX("anti_axe", scale);
        graphics.pose().pushMatrix();
        graphics.pose().translate(graphics.guiWidth() / 2.0f + hudDx,
                graphics.guiHeight() / 2.0f + 26 + hudDy);
        graphics.pose().scale(scaleX, scaleX);
        try {
            graphics.text(client.font, component,
                    Math.round(-client.font.width(component) / 2.0f), 0, 0xFFFFFF, true);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    public int getHudDx() {
        loadConfig();
        return hudDx;
    }

    public int getHudDy() {
        loadConfig();
        return hudDy;
    }

    public void setHudOffsets(int dx, int dy) {
        loadConfig();
        hudDx = Math.max(-300, Math.min(300, dx));
        hudDy = Math.max(-300, Math.min(300, dy));
        saveConfig();
    }

    public void setHudDx(int dx) {
        loadConfig();
        hudDx = Math.max(-300, Math.min(300, dx));
        saveConfig();
    }

    public void setHudDy(int dy) {
        loadConfig();
        hudDy = Math.max(-300, Math.min(300, dy));
        saveConfig();
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path current = FabricRuntime.configPath().resolve("anti-axe.properties");
        Properties properties = ConfigProperties.load(current, null);
        hudDx = ConfigProperties.integer(properties, "hudDx", 0, -300, 300);
        hudDy = ConfigProperties.integer(properties, "hudDy", 0, -300, 300);
    }

    private void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("hudDx", Integer.toString(hudDx));
        properties.setProperty("hudDy", Integer.toString(hudDy));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("anti-axe.properties"), properties,
                    "MICx AntiAXE configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save AntiAXE configuration", exception);
        }
    }

    @Override
    public void resetState() {
        AntiAxeGuard.disarm("reset");
    }
}
