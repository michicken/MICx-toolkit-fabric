package dev.micx.micxfabric;

public final class ChatTranslationRules {
    private static final int MAX_CHAT_CHARS = 256;

    private ChatTranslationRules() {
    }

    public static boolean shouldTranslate(String message) {
        if (message == null || message.isBlank() || message.trim().startsWith("/")
                || message.length() > MAX_CHAT_CHARS) return false;
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if ((c >= 0x3400 && c <= 0x4DBF) || (c >= 0x4E00 && c <= 0x9FFF)
                    || (c >= 0xF900 && c <= 0xFAFF)) return true;
        }
        return false;
    }

    public static String safeOutbound(String translated) {
        if (translated == null) return null;
        StringBuilder result = new StringBuilder(translated.length());
        boolean previousSpace = false;
        for (int i = 0; i < translated.length(); i++) {
            char c = translated.charAt(i);
            if (c == '\r' || c == '\n' || c == '\t') c = ' ';
            if (c < 32 || c == 127) continue;
            if (c == ' ') {
                if (previousSpace) continue;
                previousSpace = true;
            } else {
                previousSpace = false;
            }
            result.append(c);
            if (result.length() > MAX_CHAT_CHARS) return null;
        }
        String output = result.toString().trim();
        return output.isEmpty() || output.startsWith("/") ? null : output;
    }
}
