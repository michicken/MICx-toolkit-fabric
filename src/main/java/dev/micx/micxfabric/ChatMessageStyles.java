package dev.micx.micxfabric;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** Shared styles for client-generated MICx messages. Server components stay untouched. */
public final class ChatMessageStyles {
    public static final int INFO = 0xE7EAEE;
    public static final int SUCCESS = 0x57B98C;
    public static final int WARNING = 0xE8A73E;
    public static final int ERROR = 0xE06A6A;
    public static final int MUTED = 0x98A0AB;

    private ChatMessageStyles() {
    }

    public static Component feedback(String text) {
        return styled(prefix(text), SUCCESS);
    }

    public static Component notice(String text) {
        return styled(prefix(text), SUCCESS);
    }

    public static Component warning(String text) {
        return styled(prefix(text), WARNING);
    }

    public static Component error(String text) {
        return styled(prefix(text), ERROR);
    }

    public static Component muted(String text) {
        return styled(prefix(text), MUTED);
    }

    private static String prefix(String text) {
        return "[MICx] " + (text == null ? "" : text);
    }

    private static Component styled(String text, int color) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(color));
    }
}
