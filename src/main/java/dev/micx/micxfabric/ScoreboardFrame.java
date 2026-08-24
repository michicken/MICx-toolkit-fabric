package dev.micx.micxfabric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable, Minecraft-free view of one Zombies sidebar frame. */
public final class ScoreboardFrame {
    private static final Pattern ROUND = Pattern.compile("(?i)(?:round|波次)\\s*[:：]?\\s*(\\d+)");
    private static final Pattern LEFT = Pattern.compile("(?i)(?:zombies?(?:\\s+left)?|僵尸(?:剩余)?)\\s*[:：]?\\s*([\\d,]+)");
    private static final Pattern TIME = Pattern.compile("(?i)\\btime\\s*[:：]?\\s*((?:\\d+:)?\\d{1,2}:\\d{2})");
    private static final Pattern MAP = Pattern.compile("(?i)(?:map|地图)\\s*[:：]?\\s*(.+)");
    private static final Pattern PLAYER = Pattern.compile("^\\s*([^:：]{1,32})\\s*[:：]\\s*(.*?)\\s*$");
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final String title;
    private final List<String> lines;
    private final List<String> rawLines;
    private final int round;
    private final int zombiesLeft;
    private final int gameTimeSeconds;
    private final String map;
    private final boolean zombies;
    private final boolean alienArcadium;

    private ScoreboardFrame(String title, List<String> lines, List<String> rawLines,
                            int round, int zombiesLeft, int gameTimeSeconds, String map,
                            boolean zombies, boolean alienArcadium) {
        this.title = normalizeText(title);
        this.lines = List.copyOf(lines);
        this.rawLines = List.copyOf(rawLines);
        this.round = round;
        this.zombiesLeft = zombiesLeft;
        this.gameTimeSeconds = gameTimeSeconds;
        this.map = map;
        this.zombies = zombies;
        this.alienArcadium = alienArcadium;
    }

    public static ScoreboardFrame empty() {
        return new ScoreboardFrame("", List.of(), List.of(), -1, -1, -1, null, false, false);
    }

    /**
     * Builds a frame from lines in their visual top-to-bottom order. The raw list is optional;
     * when absent the clean list is used for status checks as well.
     */
    public static ScoreboardFrame of(String title, List<String> visualLines, List<String> rawVisualLines) {
        List<String> clean = new ArrayList<>();
        List<String> raw = new ArrayList<>();
        if (visualLines != null) {
            for (int i = 0; i < visualLines.size(); i++) {
                String line = visualLines.get(i);
                if (line == null || line.isBlank()) continue;
                String normalizedLine = normalizeText(line);
                if (normalizedLine.isBlank()) continue;
                clean.add(normalizedLine);
                String rawLine = rawVisualLines != null && i < rawVisualLines.size()
                        ? rawVisualLines.get(i) : line;
                raw.add(rawLine == null ? line.trim() : rawLine.trim());
            }
        }
        String joined = String.join(" ", clean);
        String normalized = (normalizeText(title) + " " + joined).toLowerCase(Locale.ROOT);
        boolean isZombies = normalized.contains("zombies") || normalized.contains("僵尸");
        boolean isAa = normalized.contains("alien arcadium") || normalized.contains("外星游乐园")
                || containsAaArea(clean);
        int round = findInt(ROUND, joined, -1);
        int left = findInt(LEFT, joined, -1);
        int gameTimeSeconds = findTimeSeconds(joined);
        String map = findString(MAP, clean);
        return new ScoreboardFrame(title, clean, raw, round, left, gameTimeSeconds, map, isZombies, isAa);
    }

    public String title() { return title; }
    public List<String> lines() { return lines; }
    public List<String> rawLines() { return rawLines; }
    public int round() { return round; }
    public int zombiesLeft() { return zombiesLeft; }
    public int gameTimeSeconds() { return gameTimeSeconds; }
    public String map() { return map; }
    public boolean isZombies() { return zombies; }
    public boolean isAlienArcadium() { return alienArcadium; }

    public String playerStatus(String name) {
        if (name == null || name.isBlank()) return null;
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = PLAYER.matcher(lines.get(i));
            if (!isPlayerRow(matcher) || !matcher.group(1).trim().equals(name)) continue;
            return statusFor(i, matcher.group(2));
        }
        return null;
    }

    public Map<String, String> playerStatuses() {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = PLAYER.matcher(lines.get(i));
            if (isPlayerRow(matcher)) result.put(matcher.group(1).trim(), statusFor(i, matcher.group(2)));
        }
        return Collections.unmodifiableMap(result);
    }

    public Map<String, Integer> playerGolds() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String line : lines) {
            Matcher matcher = PLAYER.matcher(line);
            if (!isPlayerRow(matcher)) continue;
            try {
                result.put(matcher.group(1).trim(), Integer.parseInt(matcher.group(2).replace(",", "").trim()));
            } catch (NumberFormatException ignored) {
                // Status lines are not gold rows.
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private String statusFor(int index, String value) {
        String clean = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        String raw = index < rawLines.size() ? rawLines.get(index) : lines.get(index);
        if (clean.contains("QUIT")) return "quit";
        if (raw.contains("✖") || clean.contains("DEAD")) return "dead";
        if (raw.contains("☠") || clean.contains("REVIVE") || clean.contains("DOWN")) return "down";
        return "alive";
    }

    public int playerGold(String name) {
        if (name == null || name.isBlank()) return -1;
        for (String line : lines) {
            Matcher matcher = PLAYER.matcher(line);
            if (!isPlayerRow(matcher) || !matcher.group(1).trim().equals(name)) continue;
            String value = matcher.group(2).replace(",", "").trim();
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static boolean containsAaArea(List<String> lines) {
        for (String line : lines) {
            String normalized = line.toLowerCase(Locale.ROOT);
            if (normalized.contains("area") && (normalized.contains("park entrance")
                    || normalized.contains("ferris wheel")
                    || normalized.contains("roller coaster")
                    || normalized.contains("bumper cars"))) return true;
        }
        return false;
    }

    private static int findInt(Pattern pattern, String text, int fallback) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        if (!matcher.find()) return fallback;
        try {
            return Integer.parseInt(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String findString(Pattern pattern, List<String> lines) {
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) return matcher.group(1).trim();
        }
        return null;
    }

    private static int findTimeSeconds(String text) {
        Matcher matcher = TIME.matcher(text == null ? "" : text);
        if (!matcher.find()) return -1;
        String[] parts = matcher.group(1).split(":");
        try {
            int seconds = Integer.parseInt(parts[parts.length - 1]);
            int minutes = Integer.parseInt(parts[parts.length - 2]);
            if (seconds > 59 || minutes > 59) return -1;
            if (parts.length == 2) return minutes * 60 + seconds;
            if (parts.length == 3) return Integer.parseInt(parts[0]) * 3600 + minutes * 60 + seconds;
        } catch (NumberFormatException ignored) {
            // Malformed scoreboard text is treated as an unavailable sample.
        }
        return -1;
    }

    private static boolean isPlayerRow(Matcher matcher) {
        if (matcher == null || !matcher.matches()) return false;
        String name = matcher.group(1).trim();
        return PLAYER_NAME.matcher(name).matches() && !isMetadataName(name);
    }

    private static boolean isMetadataName(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "round", "zombies", "zombiesleft", "time", "area", "map",
                    "difficulty", "players", "waiting", "kills" -> true;
            default -> false;
        };
    }

    /** Removes Minecraft legacy formatting and Hypixel placeholder glyphs for parsing. */
    public static String normalizeText(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length();) {
            char character = value.charAt(i++);
            if (character == '§') {
                if (i < value.length()) i++;
                continue;
            }
            if (Character.isHighSurrogate(character)) {
                if (i < value.length() && Character.isLowSurrogate(value.charAt(i))) i++;
                continue;
            }
            if (Character.isLowSurrogate(character)
                    || (character >= 0x2600 && character <= 0x27BF)
                    || (character >= 0x2B00 && character <= 0x2BFF)
                    || (character >= 0x2300 && character <= 0x23FF)
                    || character == 0xFE0F || character == 0x20E3) continue;
            builder.append(character);
        }
        return builder.toString().trim();
    }
}
