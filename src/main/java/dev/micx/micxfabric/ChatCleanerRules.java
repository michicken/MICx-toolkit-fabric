package dev.micx.micxfabric;

import java.util.regex.Pattern;

public final class ChatCleanerRules {
    private static final Pattern COUNT_SUFFIX = Pattern.compile("\\s+\\(x\\d+\\)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COPY_SUFFIX = Pattern.compile("\\s+\\[C\\]\\s*$", Pattern.CASE_INSENSITIVE);

    private ChatCleanerRules() {
    }

    public static boolean isSeparator(String text) {
        if (text == null || text.isBlank()) return false;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '-') return false;
        }
        return true;
    }

    /** Stable key shared by the message modifier and ChatComponent replacement mixin. */
    public static String canonicalKey(String text) {
        String value = text == null ? "" : text.trim();
        String previous;
        do {
            previous = value;
            value = COPY_SUFFIX.matcher(value).replaceFirst("").trim();
            value = COUNT_SUFFIX.matcher(value).replaceFirst("").trim();
        } while (!value.equals(previous));
        return value;
    }
}
