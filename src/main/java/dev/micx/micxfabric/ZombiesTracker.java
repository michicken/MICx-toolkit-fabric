package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private int gameTimeSeconds = -1;
    private long gameTimeSampleMs;
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
    private volatile long cumulativeActualMs = 0L;
    private volatile long lastSplitRoundMs = 0L;
    private volatile long lastSplitDeltaMs = 0L;
    private volatile long lastSplitTotalDeltaMs = 0L;
    private volatile long lastSplitDelta2Ms = 0L;
    private volatile long lastSplitTotalDelta2Ms = 0L;
    private volatile boolean lastSplitHasB = false;
    private volatile int lastSplitRound = 0;
    private int goldAtSample;
    private float goldPerMin;
    private static final Pattern GOLD_GAIN = Pattern.compile("\\+(\\d{1,6}) Gold(?: \\(Critical Hit\\))?", Pattern.CASE_INSENSITIVE);
    private final EcoRateTracker ecoRate = new EcoRateTracker();
    private ScoreboardFrame frame = ScoreboardFrame.empty();
    private final ZombiesEventState eventState = new ZombiesEventState();
    private final ZombiesSoundMetrics soundMetrics = new ZombiesSoundMetrics();
    private volatile long titleRoundStartMs = 0L;
    // 回合通知去重：侧栏闪断（finishOldSession→重检出）回到同一回合时，不得对模块二次 onRoundChanged 清零
    private int lastNotifiedRound = -1;
    private long lastNotifiedRoundMs = 0L;
    private volatile long waveWaveStartMs = 0L;
    private volatile int waveWaveRound = -1;
    private volatile int waveWaveIndex = -1;

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
        ecoRate.tick(now);
        if ((tickCounter++ & 3) != 0) return;
        updateFromSidebar(client.level.getScoreboard(), client);
    }

    public void reset() {
        round = 0;
        zombiesLeft = -1;
        gameTimeSeconds = -1;
        gameTimeSampleMs = 0L;
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
        // 断线/换世界属真会话终结，去重锚一并清掉（下次连接首回合必须通知）
        lastNotifiedRound = -1;
        lastNotifiedRoundMs = 0L;
        frame = ScoreboardFrame.empty();
        eventState.reset();
        ecoRate.reset();
        try { ZombiesExplorerModule.instance().onSessionReset(); } catch (Throwable ignored) {}
        soundMetrics.reset();
        try { WindowSpawnCounterModule.instance().onSessionReset(); } catch (Throwable ignored) {}
        cumulativeActualMs=0L; lastSplitRound=0; lastSplitRoundMs=0L; lastSplitDeltaMs=0L; lastSplitTotalDeltaMs=0L; lastSplitDelta2Ms=0L; lastSplitTotalDelta2Ms=0L; lastSplitHasB=false;
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

    public long roundStartMsForWaveHud() { return roundStartMs; }

    public long titleRoundStartMs() { return titleRoundStartMs; }
    long waveWaveStartMs() { return waveWaveStartMs; }
    int waveWaveRound() { return waveWaveRound; }
    int waveWaveIndex() { return waveWaveIndex; }

    void noteTitleRoundStart(int titleRound, long now) {
        if (titleRound <= 0) return;
        titleRoundStartMs = now;
    }

    public void recordWaveEntityAnchor(int waveIndex, long now) {
        // Cal-Title: entity no longer nudges baseline; countdown is title-anchored only.
    }

    void clearWaveAnchorForRound(int r) { }

    public int gameTimeSeconds() {
        return gameTimeSeconds;
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

    public long lastSplitRoundMs(){ return lastSplitRoundMs; }
    public long lastSplitDeltaMs(){ return lastSplitDeltaMs; }
    public long lastSplitTotalDeltaMs(){ return lastSplitTotalDeltaMs; }
    public long lastSplitDelta2Ms(){ return lastSplitDelta2Ms; }
    public long lastSplitTotalDelta2Ms(){ return lastSplitTotalDelta2Ms; }
    public boolean lastSplitHasB(){ return lastSplitHasB; }
    public int lastSplitRound(){ return lastSplitRound; }
    public long cumulativeActualMs(){ return cumulativeActualMs; }

    public float goldPerMin() {
        return goldPerMin;
    }

    public EcoRateTracker ecoRate() {
        return ecoRate;
    }

    static String fmtEco2min(long v) {
        return Math.abs(v) >= 1000 ? String.format(java.util.Locale.ROOT, "%.1fk", v / 1000f) : String.valueOf(v);
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

    /** Hypixel 消息常带 § 码（如 "§6+120§e Gold"），金币正则匹配前必须剥离。 */
    private static String stripFormatting(String text) {
        return text == null ? "" : text.replaceAll("§.", "");
    }

    public void onChatText(String text, long now) {
        Matcher m = GOLD_GAIN.matcher(stripFormatting(text));
        if (m.find()) { try { ecoRate.onSelfGoldGain(now, Integer.parseInt(m.group(1))); } catch (NumberFormatException ignored) {} }
        acceptEvent(ZombiesEventParser.parseChat(text), now);
    }

    public void onGameText(String text, long now) {
        Matcher m = GOLD_GAIN.matcher(stripFormatting(text));
        if (m.find()) { try { ecoRate.onSelfGoldGain(now, Integer.parseInt(m.group(1))); } catch (NumberFormatException ignored) {} }
        acceptEvent(ZombiesEventParser.parseChat(text), now);
    }

    public void onTitleText(String text, long now) {
        ZombiesEventParser.Event ev = ZombiesEventParser.parseTitle(text);
        if (ev.kind() == ZombiesEventParser.Kind.ROUND && ev.round() > 0) noteTitleRoundStart(ev.round(), now);
        acceptEvent(ev, now);
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

    /** 回合变化只通知一次：闪断重检出同一回合时不再触发模块清零（断线 reset 会清去重锚）。 */
    private void notifyRoundChanged(int value) {
        if (value == lastNotifiedRound) return;
        lastNotifiedRound = value;
        lastNotifiedRoundMs = System.currentTimeMillis();
        try { LrIndicatorModule.onRoundChanged(value); } catch (Throwable ignored) {}
        try { ZombiesExplorerModule.instance().onRoundChanged(value); } catch (Throwable ignored) {}
        try { WaveSpawnSoundModule.instance().onRoundChanged(value); } catch (Throwable ignored) {}
        try { WindowSpawnCounterModule.instance().onRoundChanged(value); } catch (Throwable ignored) {}
    }

    private void updateRound(int value, long now) {
        if (value <= 0 || value > 105) return;
        if (round > 0 && value < round && value == 1) {
            round = value; roundStartMs = now; cumulativeActualMs = 0L; lastSplitRound = 0;
            notifyRoundChanged(value);
            return;
        }
        if (value <= round) return;
        int prevRound = round; long prevStart = roundStartMs; long durationMs = prevRound >=1 && prevStart>0 ? now - prevStart : -1;
        if (durationMs>0) cumulativeActualMs += durationMs;
        long cumMs = cumulativeActualMs;
        // Announce round completion on main thread (needs Minecraft instance)
        if (prevRound >=1 && prevStart>0) {
            final int pr = prevRound; final long ps = prevStart; final long dur = durationMs; final long cm = cumMs;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    ZombiesConfig cfg = ZombiesAssistModule.instance().config();
                    String mode = cfg.roundsRecord;
                    RoundTimeNotifier.Announcement a = RoundTimeNotifier.buildAnnouncement(mode, pr, ps, System.currentTimeMillis());
                    if (a != null) {
                        RoundTimeNotifier.sendAnnouncement(a);
                        if (cfg.speedrunEnabled) {
                            SpeedrunBaseline bl = SpeedrunBaseline.get();
                            if (bl.hasBaseline()) {
                                long baseMs = bl.baselineMs(pr);
                                long cumBase = bl.cumulativeBaselineMs(pr);
                                if (baseMs>0 && cumBase>0 && dur>0) {
                                    long dR = dur - baseMs; long dT = cm - cumBase;
                                    long dR2 = 0L, dT2 = 0L; boolean hasB = false;
                                    if (bl.hasBaselineB()) {
                                        long base2 = bl.baselineMsB(pr); long cum2 = bl.cumulativeBaselineMsB(pr);
                                        if (base2>0 && cum2>0) { dR2 = dur - base2; dT2 = cm - cum2; hasB = true; }
                                    }
                                    lastSplitRound = pr; lastSplitRoundMs = dur; lastSplitDeltaMs = dR; lastSplitTotalDeltaMs = dT;
                                    lastSplitDelta2Ms = dR2; lastSplitTotalDelta2Ms = dT2; lastSplitHasB = hasB;
                                    RoundTimeNotifier.sendSplitAnnouncement(pr, dR, dT, hasB, dR2, dT2);
                                }
                            }
                        }
                    }
                });
            }
        }
        round = value;
        roundStartMs = now;
        titleRoundStartMs = now;
        clearWaveAnchorForRound(value);
        notifyRoundChanged(value);
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
            lines.add(rendered);
        }

        ScoreboardFrame next = ScoreboardFrame.of(objective.getDisplayName().getString(), lines, rawLines);
        long now = System.currentTimeMillis();
        if (!next.isZombies()) {
            markNonZombies(now);
            return;
        }

        // 陈旧侧栏守卫：标题事件刚推进回合（≤5s）、侧栏行尚未跟上时，不得把旧行当成「回合回退重开」
        boolean staleSidebarRound = round > 1 && next.round() > 0 && next.round() < round
                && lastNotifiedRound == round
                && now - lastNotifiedRoundMs < 5000L;
        if (!staleSidebarRound
                && (awaitingNewZombiesSession
                || ZombiesSessionRules.isRoundRestart(round, next.round()))) {
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
        if (next.gameTimeSeconds() >= 0) {
            gameTimeSeconds = next.gameTimeSeconds();
            gameTimeSampleMs = now;
        } else if (gameTimeSeconds >= 0 && now - gameTimeSampleMs > 60_000L) {
            gameTimeSeconds = -1;
            gameTimeSampleMs = 0L;
        }
        for (java.util.Map.Entry<String, String> entry : next.playerStatuses().entrySet()) {
            eventState.mergeScoreboardStatus(entry.getKey(), entry.getValue(), now);
        }
        if (next.round() > 0 && next.round() > round) {
            // Sidebar is fallback only — do not overwrite title-anchored roundStartMs mid-round
            if (roundStartMs <= 0L) roundStartMs = now;
            round = next.round();
            notifyRoundChanged(next.round());
        }
        if (next.zombiesLeft() >= 0) zombiesLeft = next.zombiesLeft();
        // EcoRate: sample all visible players for mate rates
        for (java.util.Map.Entry<String,Integer> e : next.playerGolds().entrySet()) {
            int g = e.getValue() == null ? -1 : e.getValue();
            if (g >= 0) ecoRate.samplePlayer(e.getKey(), g, now);
        }
        if (client != null && client.player != null) {
            String playerName = client.player.getName().getString();
            int observedGold = next.playerGold(playerName);
            if (observedGold >= 0) updateGold(observedGold, now);
        }
    }

    /** Clears only the view; a missing objective is allowed to be transient. */
    private void clearScoreboardView() {
        zombiesLeft = -1;
        gameTimeSeconds = -1;
        gameTimeSampleMs = 0L;
        inZombies = false;
        sidebarTitle = "";
        selfGold = -1;
        goldSampleMs = 0L;
        goldAtSample = 0;
        goldPerMin = 0.0f;
        frame = ScoreboardFrame.empty();
    }

    private void markNonZombies(long now) {
        // 侧栏瞬时缺失：保留战术视图（inZombies）直到确认退出，防 Hud 闪烁
        if (zombiesSessionActive && nonZombiesSince < 0L) nonZombiesSince = now;
        if (zombiesSessionActive) {
            long since = nonZombiesSince < 0L ? now : nonZombiesSince;
            if (!ZombiesSessionRules.shouldConfirmExit(true, since, now)) return;
            zombiesSessionActive = false;
            awaitingNewZombiesSession = true;
            nonZombiesSince = -1L;
            if (eventState.hasGameOverPending()) {
                // Keep the old counters until ZombiesAssist consumes Game Over on this client tick.
                inZombies = false;
                frame = ScoreboardFrame.empty();
                return;
            }
            finishOldSession();
            return;
        }
        clearScoreboardView();
    }

    private void beginZombiesSession() {
        eventState.beginNewSession();
        try { ZombiesExplorerModule.instance().onSessionReset(); } catch (Throwable ignored) {}
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
        ecoRate.reset();
        cumulativeActualMs=0L; lastSplitRound=0; lastSplitRoundMs=0L; lastSplitDeltaMs=0L; lastSplitTotalDeltaMs=0L; lastSplitDelta2Ms=0L; lastSplitTotalDelta2Ms=0L; lastSplitHasB=false;
    }

    private void finishOldSession() {
        eventState.reset();
        ecoRate.reset();
        cumulativeActualMs=0L; lastSplitRound=0; lastSplitRoundMs=0L; lastSplitDeltaMs=0L; lastSplitTotalDeltaMs=0L; lastSplitDelta2Ms=0L; lastSplitTotalDelta2Ms=0L; lastSplitHasB=false;
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
