package dev.micx.micxfabric;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ChatCleanerModule implements Module {
    private static final ChatCleanerModule INSTANCE = new ChatCleanerModule();
    private static final int MAX_TRACKED = 64;
    private final Map<String, Integer> counts = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > MAX_TRACKED;
        }
    };
    private volatile boolean enabled = true;
    private boolean registered;

    private ChatCleanerModule() {
    }

    public static ChatCleanerModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "chat_cleaner";
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
        if (this.enabled == enabled && registered == enabled) return;
        this.enabled = enabled;
        if (enabled && !registered) {
            ClientReceiveMessageEvents.MODIFY_GAME.register(this::modifyGameMessage);
            registered = true;
        }
        if (!enabled) counts.clear();
        ModuleStateStore.put(id(), enabled);
    }

    private Component modifyGameMessage(Component message, boolean overlay) {
        if (!enabled || overlay || message == null) return message;
        String text = normalize(message.getString());
        if (text.isEmpty() || ChatCleanerRules.isSeparator(text)) return message;
        int count = counts.merge(text, 1, Integer::sum);
        if (count < 2) return message;
        return message.copy().append(Component.literal("  (x" + count + ")"));
    }

    @Override
    public void tick(Minecraft client) {
        if (client == null || client.level == null) counts.clear();
    }

    @Override
    public void resetState() {
        counts.clear();
    }

    public int countFor(String text) {
        return counts.getOrDefault(normalize(text), 0);
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim();
    }
}
