package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;

/** Prints the original local-only welcome banner after a client world becomes available. */
public final class WelcomeModule implements Module {
    private static final WelcomeModule INSTANCE = new WelcomeModule();
    private boolean enabled;
    private int countdown = -1;
    private boolean hadWorld;

    private WelcomeModule() {
    }

    public static WelcomeModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "welcome";
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
        this.enabled = enabled;
        if (!enabled) {
            countdown = -1;
            hadWorld = false;
        }
        ModuleStateStore.put(id(), enabled);
    }

    @Override
    public void tick(Minecraft client) {
        if (client == null) return;
        boolean world = client.player != null && client.level != null;
        if (!world) {
            hadWorld = false;
            countdown = -1;
            return;
        }
        if (!hadWorld) {
            hadWorld = true;
            countdown = 40;
        }
        if (countdown < 0 || --countdown > 0) return;
        countdown = -1;
        printBanner(client);
    }

    private void printBanner(Minecraft client) {
        if (client.player == null) return;
        String[] lines = {
                "======== MICx Toolkit ========",
                " 制作者 / Creator: MICx",
                " 输入 /micx 打开配置面板 · open with /micx",
                " 帮开机器人: /micx hs <1-3> · summon bots",
                "================================="
        };
        for (String line : lines) client.player.sendSystemMessage(ChatMessageStyles.feedback(line));
    }
}
