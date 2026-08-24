package dev.micx.micxfabric;

public final class AutoHideRules {
    private static final String[] GAME_END_MARKERS = {
            "Reward Summary",
            "This game has been recorded"
    };
    public static final long RESTORE_COOLDOWN_MS = 60000L;
    public static final long MENU_NULL_RESTORE_MS = 2000L;

    private AutoHideRules() {}

    public static boolean isGameEndText(String text) {
        if (text == null) return false;
        for (String marker : GAME_END_MARKERS) if (text.contains(marker)) return true;
        return false;
    }

    public static boolean canTrigger(int round) { return round > 0; }

    public static boolean restoreAllowed(long hideAtMs, long nowMs, int round, boolean inZombies) {
        if (hideAtMs <= 0) return false;
        if (nowMs - hideAtMs < RESTORE_COOLDOWN_MS) return false;
        if (!inZombies) return true;
        return round == 1;
    }
}
