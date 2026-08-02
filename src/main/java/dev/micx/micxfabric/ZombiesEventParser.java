package dev.micx.micxfabric;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minecraft-free parser for the text events used by Forge ZombiesAssist. */
public final class ZombiesEventParser {
    private static final Pattern ROUND = Pattern.compile("(?i)\\bround\\s+(\\d{1,3})\\b");
    private static final Pattern KNOCK = Pattern.compile("(?i)([A-Za-z0-9_]{1,16})\\s+was\\s+knocked\\s+down\\b");
    private static final Pattern REVIVE = Pattern.compile("(?i)([A-Za-z0-9_]{1,16})\\s+revived\\s+([A-Za-z0-9_]{1,16})\\b");
    private static final Pattern REVIVE_YOU = Pattern.compile("(?i)([A-Za-z0-9_]{1,16})\\s+revived\\s+you\\b");
    private static final Pattern YOU_REVIVED = Pattern.compile("(?i)\\byou\\s+revived\\s+([A-Za-z0-9_]{1,16})\\b");
    private static final Pattern KILLED = Pattern.compile("(?i)([A-Za-z0-9_]{1,16})\\s+was\\s+(?:killed|slain)\\b");
    private static final Pattern ACTIVATED = Pattern.compile("(?i)\\bactivated\\s+(.+?)\\s+for\\s+(\\d{1,3})\\s*s\\b");

    private ZombiesEventParser() {
    }

    public enum Kind {
        ROUND,
        KNOCK,
        REVIVE,
        KILLED,
        POWERUP_ACTIVATED,
        GAME_OVER,
        FAST_REVIVE,
        UNKNOWN
    }

    public record Event(Kind kind, String subject, String actor, String powerup, int durationSeconds,
                        int round, String rawText, long actionbarRemainingMs, long actionbarModeMs) {
        public Event(Kind kind, String subject, String actor, String powerup, int durationSeconds,
                     int round, String rawText) {
            this(kind, subject, actor, powerup, durationSeconds, round, rawText, 0L, 0L);
        }

        public static Event unknown(String rawText) {
            return new Event(Kind.UNKNOWN, null, null, null, 0, -1, rawText);
        }
    }

    public static Event parseTitle(String text) {
        String clean = normalize(text);
        Matcher round = ROUND.matcher(clean);
        if (round.find()) {
            return new Event(Kind.ROUND, null, null, null, 0, parseInt(round.group(1), -1), clean);
        }
        // Forge 的 Game Over 由聊天事件负责；title 非 Round 信号不升级为事件。
        return Event.unknown(clean);
    }

    public static Event parseSubtitle(String text) {
        String clean = normalize(text);
        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.startsWith("double gold")) {
            return new Event(Kind.POWERUP_ACTIVATED, null, null, "Double Gold", 30, -1, clean);
        }
        if (lower.startsWith("shopping spree")) {
            return new Event(Kind.POWERUP_ACTIVATED, null, null, "Shopping Spree", 20, -1, clean);
        }
        if (lower.startsWith("insta kill")) {
            return new Event(Kind.POWERUP_ACTIVATED, null, null, "Insta Kill", 10, -1, clean);
        }
        // Forge subtitle capture only recognizes these three activation titles.
        return Event.unknown(clean);
    }

    public static Event parseChat(String text) {
        String clean = normalize(text);
        if (clean.isEmpty()) return Event.unknown(clean);

        Matcher matcher = KNOCK.matcher(clean);
        if (matcher.find()) return new Event(Kind.KNOCK, matcher.group(1), null, null, 0, -1, clean);
        matcher = REVIVE_YOU.matcher(clean);
        if (matcher.find()) return new Event(Kind.REVIVE, "you", matcher.group(1), null, 0, -1, clean);
        matcher = YOU_REVIVED.matcher(clean);
        if (matcher.find()) return new Event(Kind.REVIVE, matcher.group(1), "you", null, 0, -1, clean);
        matcher = REVIVE.matcher(clean);
        if (matcher.find()) return new Event(Kind.REVIVE, matcher.group(2), matcher.group(1), null, 0, -1, clean);
        matcher = KILLED.matcher(clean);
        if (matcher.find()) return new Event(Kind.KILLED, matcher.group(1), null, null, 0, -1, clean);
        if (clean.matches("(?i).*\\byou\\s+died\\b.*")) {
            return new Event(Kind.KILLED, "you", null, null, 0, -1, clean);
        }
        Event powerup = parsePowerup(clean);
        if (powerup.kind() == Kind.POWERUP_ACTIVATED) return powerup;
        if (isGameOver(clean)) return new Event(Kind.GAME_OVER, null, null, null, 0, -1, clean);
        return Event.unknown(clean);
    }

    public static Event parseActionBar(String text) {
        String clean = normalize(text);
        FastReviveDecisionEngine.ParsedActionbar rescue =
                FastReviveDecisionEngine.parseActionbar(clean);
        if (rescue != null) {
            long mode = rescue.kind == FastReviveDecisionEngine.ActionbarKind.HOLD
                    ? 0L : (rescue.remainingMs > FastReviveDecisionEngine.FAST_REVIVE_MS
                    ? 1_500L : FastReviveDecisionEngine.FAST_REVIVE_MS);
            return new Event(Kind.FAST_REVIVE, rescue.target, null, null, 0, -1,
                    clean, rescue.remainingMs, mode);
        }
        return Event.unknown(clean);
    }

    public static String normalize(String text) {
        if (text == null) return "";
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '\u00a7' && i + 1 < text.length()) {
                i++;
                continue;
            }
            result.append(current);
        }
        return result.toString().replace('\u00a0', ' ').trim();
    }

    private static Event parsePowerup(String clean) {
        Matcher matcher = ACTIVATED.matcher(clean);
        if (!matcher.find()) return Event.unknown(clean);
        int seconds = parseInt(matcher.group(2), -1);
        if (seconds < 1 || seconds > 120) return Event.unknown(clean);
        return new Event(Kind.POWERUP_ACTIVATED, null, null, matcher.group(1).trim(), seconds, -1, clean);
    }

    private static boolean isGameOver(String clean) {
        String lower = clean.toLowerCase(Locale.ROOT);
        return lower.contains("game over") || lower.contains("zombies -") || lower.contains("game_over")
                || lower.contains("结算");
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
