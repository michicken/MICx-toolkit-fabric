package dev.micx.micxfabric;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

/** Idempotent Fabric message bridge for the client-thread Zombies reducer. */
public final class ZombiesEventBridge {
    private static boolean initialized;

    private ZombiesEventBridge() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, boundType, receptionTime) -> {
            if (!ZombiesAssistModule.instance().enabled() || message == null) return;
            ZombiesTracker.instance().onChatText(message.getString(), System.currentTimeMillis());
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!ZombiesAssistModule.instance().enabled() || message == null) return;
            ZombiesTracker.instance().onGameText(message.getString(), System.currentTimeMillis());
        });
    }

    public static void reset() {
        ZombiesTracker.instance().reset();
    }
}
