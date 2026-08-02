package dev.micx.micxfabric;

public final class ChatCleanerRules {
    private ChatCleanerRules() {
    }

    public static boolean isSeparator(String text) {
        if (text == null || text.isBlank()) return false;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '-') return false;
        }
        return true;
    }
}
