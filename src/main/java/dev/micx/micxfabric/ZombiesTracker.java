package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic Fabric-side scoreboard tracker for the migrated Zombies HUD core. */
public final class ZombiesTracker {
    private static final ZombiesTracker INSTANCE = new ZombiesTracker();
    private static final Comparator<PlayerScoreEntry> DISPLAY_ORDER =
            Comparator.comparingInt(PlayerScoreEntry::value).reversed()
                    .thenComparing(PlayerScoreEntry::owner);

    private int round;
    private int zombiesLeft = -1;
    private long roundStartMs;
    private boolean inZombies;
    private boolean inAlienArcadium;
    /** True while the current world still owns a Zombies event session. */
    private boolean zombiesSessionActive;
    /** A game-over event is retained until the automatic-behavior pass consumes it. */
    private boolean awaitingNewZombiesSession;
    private boolean sessionResetPending;
    private long nonZombiesSince = -1L;
    private long eventGeneration;
    private Object activeLevel;
    private Object eventLevel;
    private int tickCounter;
    private String sidebarTitle = "";
    private int selfGold = -1;
    private long goldSampleMs;
    private int goldAtSample;
    private float goldPerMin;
    private ScoreboardFrame frame = ScoreboardFrame.empty();
    private final ZombiesEventState eventState = new ZombiesEventState();
    private final ZombiesSoundMetrics soundMetrics = new ZombiesSoundMetrics();

    private ZombiesTracker() {
    }

    public static ZombiesTracker instance() {
        return INSTANCE;
    }

    public void tick(Minecraft client) {
        if (client == null || client.level == null || client.player == null) {
            reset();
            return;
        }
        if (activeLevel != client.level) {
            reset();
            activeLevel = client.level;
            eventLevel = client.level;
        }
        long now = System.currentTimeMillis();
        eventState.expirePowerUps(now);
        if ((tickCounter++ & 3) != 0) return;
        updateFromSidebar(client.level.getScoreboard(), client);
    }

    public void reset() {
        round = 0;
        zombiesLeft = -1;
        roundStartMs = 0L;
        inZombies = false;
        inAlienArcadium = false;
        zombiesSessionActive = false;
        awaitingNewZombiesSession = false;
        sessionResetPending = false;
        nonZombiesSince = -1L;
        sidebarTitle = "";
        selfGold = -1;
        goldSampleMs = 0L;
        goldAtSample = 0;
        goldPerMin = 0.0f;
        activeLevel = null;
        eventLevel = null;
        eventGeneration++;
        tickCounter = 0;
        frame = ScoreboardFrame.empty();
        eventState.reset();
        soundMetrics.reset();
    }

    public int round() {
        return round;
    }

    public int zombiesLeft() {
        return zombiesLeft;
    }

    public long roundStartMs() {
        return roundStartMs;
    }

    public boolean isInZombies() {
        return inZombies;
    }

    public boolean isInAlienArcadium() {
        return inAlienArcadium;
    }

    public String sidebarTitle() {
        return sidebarTitle;
    }

    public ScoreboardFrame frame() {
        return frame;
    }

    public String playerStatus(String name) {
        return frame.playerStatus(name);
    }

    public int playerGold(String name) {
        return frame.playerGold(name);
    }

    public int selfGold() {
        return selfGold;
    }

    public float goldPerMin() {
        return goldPerMin;
    }

    public ZombiesEventState eventState() {
        return eventState;
    }

    public int lrUses() {
        return soundMetrics.lrUses();
    }

    public int hits() {
        return soundMetrics.hits();
    }

    public int crits() {
        return soundMetrics.crits();
    }

    public boolean isFiring(String name, long now) {
        return soundMetrics.isFiring(name, now);
    }

    public String fireWeapon(String name, long now) {
        return soundMetrics.fireWeapon(name, now);
    }

    public void onChatText(String text, long now) {
        acceptEvent(ZombiesEventParser.parseChat(text), now);
    }

    public void onGameText(String text, long now) {
        acceptEvent(ZombiesEventParser.parseChat(text), now);
    }

    public void onTitleText(String text, long now) {
        acceptEvent(ZombiesEventParser.parseTitle(text), now);
    }

    public void onSubtitleText(String text, long now) {
        acceptEvent(ZombiesEventParser.parseSubtitle(text), now);
    }

    public void onActionBarText(String text, long now) {
        acceptEvent(ZombiesEventParser.parseActionBar(text), now);
    }

    private void acceptEvent(ZombiesEventParser.Event event, long now) {
        if (event == null) return;
        boolean validContext = eventContextValid();
        if (!validContext && event.kind() == ZombiesEventParser.Kind.ROUND) {
            validContext = prewarmRoundTitleContext();
        }
        if (!validContext) return;
        boolean localRevive = event.kind() == ZombiesEventParser.Kind.REVIVE
                && "you".equalsIgnoreCase(event.subject());
        ZombiesEventParser.Event normalized = normalizeLocalTarget(event);
        eventState.accept(normalized, now);
        if (localRevive) eventState.markLocalReviveProtection(now);
        if (normalized.kind() == ZombiesEventParser.Kind.ROUND && normalized.round() > 0) {
            updateRound(normalized.round(), now);
        }
    }

    private boolean prewarmRoundTitleContext() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || awaitingNewZombiesSession) return false;
        if (activeLevel == null) {
            activeLevel = client.level;
            eventLevel = client.level;
        }
        if (activeLevel != client.level || eventLevel != client.level) return false;
        if (!zombiesSessionActive) beginZombiesSession();
        zombiesSessionActive = true;
        inZombies = true;
        eventLevel = activeLevel;
        return true;
    }

    private boolean eventContextValid() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.level != null
                && client.level == activeLevel && client.level == eventLevel
                && zombiesSessionActive && !awaitingNewZombiesSession;
    }

    private static ZombiesEventParser.Event normalizeLocalTarget(ZombiesEventParser.Event event) {
        if (event == null || event.subject() == null
                || !"you".equalsIgnoreCase(event.subject())) return event;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return event;
        return new ZombiesEventParser.Event(event.kind(), client.player.getName().getString(),
                event.actor(), event.powerup(), event.durationSeconds(), event.round(), event.rawText(),
                event.actionbarRemainingMs(), event.actionbarModeMs());
    }

    public void onSound(String soundId, float pitch, double x, double y, double z, long now) {
        if (!eventContextValid() || soundId == null || soundId.isBlank()) return;
        Minecraft client = Minecraft.getInstance();
        List<ZombiesSoundMetrics.PlayerPoint> players = new ArrayList<>();
        if (client != null && client.level != null) {
            for (Player player : client.level.players()) {
                if (player == null || player.isRemoved()) continue;
                players.add(new ZombiesSoundMetrics.PlayerPoint(player.getName().getString(),
                        player.getX(), player.getY(), player.getZ()));
            }
        }
        soundMetrics.observe(soundId, pitch, x, y, z, now, inAlienArcadium, players);
    }

    private void updateRound(int value, long now) {
        if (value <= 0 || value > 105 || value <= round) return;
        round = value;
        roundStartMs = now;
    }

    private void updateFromSidebar(Scoreboard scoreboard, Minecraft client) {
        if (scoreboard == null) {
            markNonZombies(System.currentTimeMillis());
            return;
        }
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) {
            markNonZombies(System.currentTimeMillis());
            return;
        }

        List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(objective));
        entries.removeIf(PlayerScoreEntry::isHidden);
        entries.sort(DISPLAY_ORDER);
        List<String> lines = new ArrayList<>(entries.size());
        List<String> rawLines = new ArrayList<>(entries.size());
        for (PlayerScoreEntry entry : entries) {
            String owner = entry.owner();
            Component display = entry.display();
            String entryText = display == null ? owner : display.getString();
            PlayerTeam team = scoreboard.getPlayersTeam(owner);
            String rendered = renderTeamLine(team, entryText, owner);
            if (rendered.isBlank()) continue;
            rawLines.add(rendered);
            lines.add(stripPlaceholders(rendered));
        }

        ScoreboardFrame next = ScoreboardFrame.of(objective.getDisplayName().getString(), lines, rawLines);
        long now = System.currentTimeMillis();
        if (!next.isZombies()) {
            markNonZombies(now);
            return;
        }

        if (awaitingNewZombiesSession
                || ZombiesSessionRules.isRoundRestart(round, next.round())) {
            if (eventState.hasGameOverPending()) {
                // Do not replace the old counters before the post-game consumer has read them.
                clearScoreboardView();
                return;
            }
            beginZombiesSession();
        } else if (!zombiesSessionActive) {
            beginZombiesSession();
        }
        zombiesSessionActive = true;
        awaitingNewZombiesSession = false;
        nonZombiesSince = -1L;
        eventLevel = activeLevel;
        inZombies = true;
        frame = next;
        sidebarTitle = next.title();
        if (next.isAlienArcadium()) inAlienArcadium = true;
        for (java.util.Map.Entry<String, String> entry : next.playerStatuses().entrySet()) {
            eventState.mergeScoreboardStatus(entry.getKey(), entry.getValue(), now);
        }
        if (next.round() > 0 && next.round() > round) {
            round = next.round();
            roundStartMs = now;
        }
        if (next.zombiesLeft() >= 0) zombiesLeft = next.zombiesLeft();
        if (client != null && client.player != null) {
            String playerName = client.player.getName().getString();
            int observedGold = next.playerGold(playerName);
            if (observedGold >= 0) updateGold(observedGold, now);
        }
    }

    /** Clears only the view; a missing objective is allowed to be transient. */
    private void clearScoreboardView() {
        zombiesLeft = -1;
        inZombies = false;
        sidebarTitle = "";
        selfGold = -1;
        goldSampleMs = 0L;
        goldAtSample = 0;
        goldPerMin = 0.0f;
        frame = ScoreboardFrame.empty();
    }

    private void markNonZombies(long now) {
        clearScoreboardView();
        if (!zombiesSessionActive) return;
        if (nonZombiesSince < 0L) nonZombiesSince = now;
        if (!ZombiesSessionRules.shouldConfirmExit(true, nonZombiesSince, now)) return;

        zombiesSessionActive = false;
        awaitingNewZombiesSession = true;
        nonZombiesSince = -1L;
        if (eventState.hasGameOverPending()) {
            // Keep the old counters until ZombiesAssist consumes Game Over on this client tick.
            sessionResetPending = true;
            return;
        }
        finishOldSession();
    }

    private void beginZombiesSession() {
        eventState.beginNewSession();
        soundMetrics.reset();
        eventGeneration++;
        zombiesSessionActive = true;
        awaitingNewZombiesSession = false;
        sessionResetPending = false;
        nonZombiesSince = -1L;
        round = 0;
        zombiesLeft = -1;
        roundStartMs = 0L;
        inAlienArcadium = false;
        selfGold = -1;
        goldSampleMs = 0L;
        goldAtSample = 0;
        goldPerMin = 0.0f;
    }

    private void finishOldSession() {
        eventState.reset();
        eventGeneration++;
        sessionResetPending = false;
        round = 0;
        zombiesLeft = -1;
        roundStartMs = 0L;
        inAlienArcadium = false;
    }

    /** Called after the automatic behavior pass has consumed the retained Game Over event. */
    public void finishGameOverSession() {
        if (!sessionResetPending || eventState.hasGameOverPending()) return;
        finishOldSession();
    }

    public long eventGeneration() {
        return eventGeneration;
    }

    private void updateGold(int observedGold, long now) {
        if (selfGold >= 0 && observedGold < selfGold) {
            goldSampleMs = now;
            goldAtSample = observedGold;
            goldPerMin = 0.0f;
        } else if (goldSampleMs == 0L) {
            goldSampleMs = now;
            goldAtSample = observedGold;
        } else if (now - goldSampleMs >= 30_000L) {
            int earned = observedGold - goldAtSample;
            long elapsed = now - goldSampleMs;
            if (earned >= 0 && elapsed > 0L) goldPerMin = earned * 60_000.0f / elapsed;
            goldSampleMs = now;
            goldAtSample = observedGold;
        }
        selfGold = observedGold;
    }

    private static String renderTeamLine(PlayerTeam team, String entryText, String owner) {
        if (team == null) return entryText == null ? owner : entryText;
        try {
            return team.getFormattedName(Component.literal(entryText == null ? owner : entryText)).getString();
        } catch (RuntimeException ignored) {
            String prefix = team.getPlayerPrefix() == null ? "" : team.getPlayerPrefix().getString();
            String suffix = team.getPlayerSuffix() == null ? "" : team.getPlayerSuffix().getString();
            return prefix + (entryText == null ? owner : entryText) + suffix;
        }
    }

    private static String stripPlaceholders(String value) {
        if (value == null) return "";
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (Character.isHighSurrogate(character) || Character.isLowSurrogate(character)) continue;
            if ((character >= 0x2600 && character <= 0x27BF)
                    || (character >= 0x2B00 && character <= 0x2BFF)
                    || (character >= 0x2300 && character <= 0x23FF)
                    || character == 0xFE0F || character == 0x20E3) continue;
            builder.append(character);
        }
        return builder.toString().trim();
    }
}
