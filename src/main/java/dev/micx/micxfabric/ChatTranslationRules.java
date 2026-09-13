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

    /** [T] 点击翻译只排除空行和命令，允许英文、中文或中英混合聊天消息（对照 1.8.9）。 */
    public static boolean shouldTranslateDisplayedMessage(String message) {
        return message != null && !message.trim().isEmpty() && !message.trim().startsWith("/");
    }

    /** 剥掉 ChatCleaner 追加的 "  (x2)" 去重后缀，[T] 存原文时使用。 */
    public static String stripCleanerCountSuffix(String text) {
        if (text == null) return null;
        String trimmed = text.stripTrailing();
        if (!trimmed.endsWith(")")) return text;
        int open = trimmed.lastIndexOf('(');
        if (open <= 0) return text;
        int digitsStart = open + 1;
        if (digitsStart < trimmed.length() - 1 && trimmed.charAt(digitsStart) == 'x') digitsStart++;
        if (digitsStart >= trimmed.length() - 1) return text;
        for (int i = digitsStart; i < trimmed.length() - 1; i++) {
            char c = trimmed.charAt(i);
            if (c < '0' || c > '9') return text;
        }
        return trimmed.substring(0, open).stripTrailing();
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
