package dev.micx.micxfabric;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure decision logic for the per-player Fast Revive HUD. */
public final class FastReviveDecisionEngine {
    public static final long BENEFIT_WINDOW_MS = 800L;
    public static final long FAST_REVIVE_MS = 300L;
    public static final long REACTION_MS = 100L;
    public static final long INPUT_GUARD_MS = 75L;
    public static final long MIN_RESTART_GAIN_MS = 100L;
    public static final long MAX_ADVANCE_MS = 600L;
    public static final long ALL_READY_HOLD_MS = 3_000L;

    private static final Pattern HOLD = Pattern.compile("^Hold SNEAK to revive (\\w{1,16})\\.$");
    private static final Pattern REVIVING = Pattern.compile(
            "^Reviving (\\w{1,16}) - ([0-9]+(?:\\.[0-9]+)?)s$");

    private FastReviveDecisionEngine() {
    }

    public enum Action {
        READY,
        WAIT,
        COOLING,
        RESHIFT,
        KEEP_HOLDING,
        FAST_REVIVING
    }

    public enum Tone {
        GREEN,
        YELLOW
    }

    public enum ActionbarKind {
        HOLD,
        REVIVING
    }

    public static final class ParsedActionbar {
        public final ActionbarKind kind;
        public final String target;
        public final long remainingMs;

        private ParsedActionbar(ActionbarKind kind, String target, long remainingMs) {
            this.kind = kind;
            this.target = target;
            this.remainingMs = remainingMs;
        }
    }

    public static final class Latency {
        public final int rttMs;
        public final int jitterMs;
        public final String source;
        public final boolean highConfidence;

        public Latency(int rttMs, int jitterMs, String source, boolean highConfidence) {
            this.rttMs = Math.max(0, rttMs);
            this.jitterMs = Math.max(0, jitterMs);
            this.source = source == null ? "unknown" : source;
            this.highConfidence = highConfidence;
        }
    }

    public static final class PlayerInput {
        public final String name;
        public final long rawCooldownMs;
        public final boolean activeTarget;
        public final long actionbarModeMs;
        public final long actionbarRemainingMs;

        public PlayerInput(String name, long rawCooldownMs) {
            this(name, rawCooldownMs, false, 0L, 0L);
        }

        public PlayerInput(String name, long rawCooldownMs, boolean activeTarget,
                           long actionbarModeMs, long actionbarRemainingMs) {
            this.name = name;
            this.rawCooldownMs = Math.max(0L, rawCooldownMs);
            this.activeTarget = activeTarget;
            this.actionbarModeMs = Math.max(0L, actionbarModeMs);
            this.actionbarRemainingMs = Math.max(0L, actionbarRemainingMs);
        }
    }

    public static final class Row {
        public final String name;
        public final Action action;
        public final long rawCooldownMs;
        public final long adjustedCooldownMs;
        public final long safeAdvanceMs;
        public final String text;
        public final boolean green;
        public final Tone tone;

        private Row(String name, Action action, long rawCooldownMs, long adjustedCooldownMs,
                    long safeAdvanceMs, String text, Tone tone) {
            this.name = name;
            this.action = action;
            this.rawCooldownMs = rawCooldownMs;
            this.adjustedCooldownMs = adjustedCooldownMs;
            this.safeAdvanceMs = safeAdvanceMs;
            this.text = text;
            this.tone = tone;
            this.green = tone == Tone.GREEN;
        }
    }

    public static final class Visibility {
        public final boolean visible;
        public final long allReadySince;

        private Visibility(boolean visible, long allReadySince) {
            this.visible = visible;
            this.allReadySince = allReadySince;
        }
    }

    public static ParsedActionbar parseActionbar(String text) {
        if (text == null) return null;
        String clean = text.replaceAll("\\u00a7[0-9A-FK-ORa-fk-or]", "")
                .replace('\u00a0', ' ').trim().replaceAll("\\s+", " ")
                .replaceFirst("\\s*\\[C\\]$", "").trim();
        Matcher hold = HOLD.matcher(clean);
        if (hold.matches()) return new ParsedActionbar(ActionbarKind.HOLD, hold.group(1), 0L);
        Matcher reviving = REVIVING.matcher(clean);
        if (!reviving.matches()) return null;
        try {
            long remaining = Math.round(Double.parseDouble(reviving.group(2)) * 1_000.0);
            return new ParsedActionbar(ActionbarKind.REVIVING, reviving.group(1), remaining);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static long safeAdvanceMs(Latency latency) {
        if (latency == null || !latency.highConfidence || latency.rttMs <= 0) return 0L;
        long value = latency.rttMs - latency.jitterMs + REACTION_MS - INPUT_GUARD_MS;
        return clamp(value, 0L, MAX_ADVANCE_MS);
    }

    public static Visibility updateVisibility(boolean hasDownedPlayers, boolean allReady,
                                              long previousAllReadySince, long now) {
        if (!hasDownedPlayers) return new Visibility(false, 0L);
        if (!allReady) return new Visibility(true, 0L);
        long since = previousAllReadySince == 0L ? now : previousAllReadySince;
        return new Visibility(now - since < ALL_READY_HOLD_MS, since);
    }

    public static List<Row> buildRows(Collection<PlayerInput> players, Latency latency) {
        if (players == null || players.isEmpty()) return Collections.emptyList();
        long advance = safeAdvanceMs(latency);
        List<Row> rows = new ArrayList<>();
        for (PlayerInput input : players) {
            if (input == null || input.name == null || input.name.isEmpty()) continue;
            long adjusted = Math.max(0L, input.rawCooldownMs - advance);
            Action action;
            Tone tone;
            String text;
            if (input.activeTarget && input.actionbarModeMs > 0L) {
                if (input.actionbarModeMs <= FAST_REVIVE_MS) {
                    action = Action.FAST_REVIVING;
                    tone = Tone.GREEN;
                    text = input.name + ": 0.3秒快速救援中";
                } else {
                    long restartEta = adjusted + REACTION_MS + FAST_REVIVE_MS;
                    boolean restartWins = input.actionbarRemainingMs - restartEta >= MIN_RESTART_GAIN_MS;
                    if (restartWins && adjusted <= 0L) {
                        action = Action.RESHIFT;
                        tone = Tone.GREEN;
                        text = input.name + ": 已进入1.5秒，松开重按 SHIFT";
                    } else if (restartWins) {
                        action = Action.WAIT;
                        tone = Tone.YELLOW;
                        text = input.name + ": 已进入1.5秒，松开，" + seconds(adjusted, 2)
                                + "秒后重按 SHIFT";
                    } else {
                        action = Action.KEEP_HOLDING;
                        tone = Tone.YELLOW;
                        text = input.name + ": 已进入1.5秒，继续救援 "
                                + seconds(input.actionbarRemainingMs, 1) + "秒";
                    }
                }
            } else if (adjusted <= 0L) {
                action = Action.READY;
                tone = Tone.GREEN;
                text = input.name + ": 快速救援就绪";
            } else if (adjusted <= BENEFIT_WINDOW_MS) {
                action = Action.WAIT;
                tone = Tone.YELLOW;
                text = input.name + ": 等待 " + seconds(adjusted, 2) + "秒再救";
            } else {
                action = Action.COOLING;
                tone = Tone.YELLOW;
                text = input.name + ": 快速救援冷却 " + seconds(adjusted, 2) + "秒";
            }
            rows.add(new Row(input.name, action, input.rawCooldownMs, adjusted, advance, text, tone));
        }
        rows.sort(new Comparator<Row>() {
            @Override
            public int compare(Row left, Row right) {
                int byPriority = Integer.compare(priority(left), priority(right));
                if (byPriority != 0) return byPriority;
                int byTime = Long.compare(left.adjustedCooldownMs, right.adjustedCooldownMs);
                if (byTime != 0) return byTime;
                return String.CASE_INSENSITIVE_ORDER.compare(left.name, right.name);
            }
        });
        return rows;
    }

    public static List<Row> limitRowsIncluding(List<Row> sortedRows, int limit, String requiredName) {
        if (sortedRows == null || sortedRows.isEmpty() || limit <= 0) return Collections.emptyList();
        int count = Math.min(limit, sortedRows.size());
        List<Row> result = new ArrayList<>(sortedRows.subList(0, count));
        if (requiredName == null || sortedRows.size() <= limit) return result;
        for (Row row : result) if (requiredName.equals(row.name)) return result;
        for (Row row : sortedRows) {
            if (requiredName.equals(row.name)) {
                result.set(result.size() - 1, row);
                break;
            }
        }
        return result;
    }

    private static int priority(Row row) {
        if (row.green) return 0;
        if (row.action == Action.WAIT) return 1;
        if (row.action == Action.KEEP_HOLDING) return 2;
        return 3;
    }

    private static String seconds(long ms, int decimals) {
        return String.format(Locale.ROOT, decimals == 1 ? "%.1f" : "%.2f", ms / 1_000.0);
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
