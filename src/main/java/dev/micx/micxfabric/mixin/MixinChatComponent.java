package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.ChatCleanerModule;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.regex.Pattern;

@Mixin(ChatComponent.class)
public class MixinChatComponent {
    private static final Pattern COUNT_SUFFIX = Pattern.compile("\\s+\\(x\\d+\\)$");

    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow private void refreshTrimmedMessages() {
        throw new AssertionError();
    }

    @Inject(method = "addMessage", at = @At("HEAD"))
    private void micx$collapseDuplicate(
            Component message,
            MessageSignature signature,
            GuiMessageSource source,
            net.minecraft.client.multiplayer.chat.GuiMessageTag tag,
            CallbackInfo ci) {
        if (!ChatCleanerModule.instance().enabled() || message == null) return;
        String key = normalize(message.getString());
        if (key.isEmpty()) return;
        if (allMessages.removeIf(existing -> key.equals(normalize(existing.content().getString())))) {
            refreshTrimmedMessages();
        }
    }

    private static String normalize(String text) {
        String value = text == null ? "" : text.trim();
        return COUNT_SUFFIX.matcher(value).replaceFirst("");
    }
}
