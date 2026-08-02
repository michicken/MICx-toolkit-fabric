package dev.micx.micxfabric;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** Adds a vanilla-supported copy-to-clipboard action to received chat lines. */
public final class ChatCopyModule implements Module {
    private static final ChatCopyModule INSTANCE = new ChatCopyModule();
    private boolean enabled;
    private boolean registered;

    private ChatCopyModule() {
    }

    public static ChatCopyModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "chat_copy";
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
        if (enabled && !registered) {
            ClientReceiveMessageEvents.MODIFY_GAME.register(this::decorate);
            registered = true;
        }
        ModuleStateStore.put(id(), enabled);
    }

    private Component decorate(Component message, boolean overlay) {
        if (!enabled || overlay || message == null || message.getString().isBlank()) return message;
        Component suffix = Component.literal("  [C]")
                .withStyle(Style.EMPTY.withColor(0xFF666666)
                        .withClickEvent(new ClickEvent.CopyToClipboard(message.getString())));
        return message.copy().append(suffix);
    }

    @Override
    public void tick(Minecraft client) {
    }
}
