package dev.micx.micxfabric;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Client-thread state reducer for text events; no Minecraft classes are required. */
public final class ZombiesEventState {
    private final Map<String, Long> downSince = new LinkedHashMap<>();
    private final Map<String, String> statuses = new LinkedHashMap<>();
    private final Map<String, Long> frCooldownUntil = new LinkedHashMap<>();
    private final Map<String, Long> sidebarDownSeen = new LinkedHashMap<>();
    private final Map<String, Long> aliveCandidates = new LinkedHashMap<>();
    private final Map<String, Long> deadCandidates = new LinkedHashMap<>();
    private final PowerUpTimer powerUps = new PowerUpTimer();
    private final AutoBehaviorLatch latches = new AutoBehaviorLatch();
    private long reviveProtectionUntil;
    private String localRescueTarget;
    private long localRescueModeMs;
    private long localRescueRemainingMs;
    private long localRescueObservedAt;
    private long localRescueAttemptSeq;
    private int totalDowns;
    private int totalDeaths;
    private int totalRevives;
    private boolean gameOverPending;

    public void reset() {
        downSince.clear();
        statuses.clear();
        frCooldownUntil.clear();
        sidebarDownSeen.clear();
        aliveCandidates.clear();
        deadCandidates.clear();
        powerUps.clear();
        latches.clear();
        reviveProtectionUntil = 0L;
        localRescueTarget = null;
        localRescueModeMs = 0L;
        localRescueRemainingMs = 0L;
        localRescueObservedAt = 0L;
        localRescueAttemptSeq = 0L;
        totalDowns = 0;
        totalDeaths = 0;
        totalRevives = 0;
        gameOverPending = false;
    }

    public void accept(ZombiesEventParser.Event event, long now) {
        if (event == null) return;
        switch (event.kind()) {
            case KNOCK -> {
                if (event.subject() != null && downSince.putIfAbsent(event.subject(), now) == null) totalDowns++;
                if (event.subject() != null) {
                    sidebarDownSeen.remove(event.subject());
                    aliveCandidates.remove(event.subject());
                    deadCandidates.remove(event.subject());
                }
                setStatus(event.subject(), "down");
            }
            case REVIVE -> {
                String target = event.subject();
                if (target == null && event.actor() != null) target = "you";
                if (target != null) {
                    String previous = statuses.get(target);
                    boolean wasDown = downSince.remove(target) != null || "down".equals(previous);
                    sidebarDownSeen.remove(target);
                    aliveCandidates.remove(target);
                    deadCandidates.remove(target);
                    statuses.put(target, "alive");
                    if (wasDown) {
                        totalRevives++;
                        frCooldownUntil.put(target, now + 5_000L);
                        if ("you".equalsIgnoreCase(target)) {
                            reviveProtectionUntil = now + 1_400L;
                        }
                    }
                    if (target.equals(localRescueTarget)) clearLocalRescue();
                }
            }
            case KILLED -> {
                if (event.subject() != null) {
                    String previous = statuses.put(event.subject(), "dead");
                    downSince.remove(event.subject());
                    sidebarDownSeen.remove(event.subject());
                    aliveCandidates.remove(event.subject());
                    deadCandidates.remove(event.subject());
                    if (!"dead".equals(previous)) totalDeaths++;
                }
            }
            case POWERUP_ACTIVATED -> {
                powerUps.activate(event.powerup(), event.durationSeconds(), now);
                try { dev.micx.micxfabric.ZombiesPuRoundsLatch.onPowerup(event.powerup(), now); } catch (Throwable ignored) {}
            }
            case FAST_REVIVE -> updateLocalRescue(event, now);
            case GAME_OVER -> gameOverPending = true;
            default -> {
            }
        }
        powerUps.expire(now);
    }

    public void expirePowerUps(long now) {
        powerUps.expire(now);
    }

    public Map<String, Long> downSince() { return Collections.unmodifiableMap(downSince); }
    public Map<String, String> statuses() { return Collections.unmodifiableMap(statuses); }
    public PowerUpTimer powerUps() { return powerUps; }
    public AutoBehaviorLatch latches() { return latches; }
    public int totalDowns() { return totalDowns; }
    public int totalDeaths() { return totalDeaths; }
    public int totalRevives() { return totalRevives; }

    public Map<String, Long> frCooldownUntil() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(frCooldownUntil));
    }

    public long frCooldownRemainingMs(String name, long now) {
        if (name == null) return 0L;
        long remaining = frCooldownUntil.getOrDefault(name, 0L) - now;
        if (remaining <= 0L) {
            frCooldownUntil.remove(name);
            return 0L;
        }
        return remaining;
    }

    public long reviveProtectionRemainingMs(long now) {
        return Math.max(0L, reviveProtectionUntil - now);
    }

    /** Returns the current local rescue action while its actionbar observation is fresh. */
    public RescueAction localRescueAction(long now) {
        if (localRescueTarget == null || now - localRescueObservedAt > 750L) return null;
        long remaining = Math.max(0L, localRescueRemainingMs - Math.max(0L, now - localRescueObservedAt));
        return new RescueAction(localRescueTarget, localRescueModeMs, remaining,
                localRescueObservedAt, localRescueAttemptSeq);
    }

    public record RescueAction(String target, long modeMs, long remainingMs,
                               long observedAt, long attemptSeq) {
    }

    /** Marks the local player's short post-revive protection window. */
    public void markLocalReviveProtection(long now) {
        reviveProtectionUntil = now + 1_400L;
    }

    public boolean hasGameOverPending() {
        return gameOverPending;
    }

    /** Clears the previous game state before a newly confirmed Zombies session. */
    public void beginNewSession() {
        boolean pendingGameOver = gameOverPending;
        reset();
        gameOverPending = pendingGameOver;
    }

    public void mergeScoreboardStatus(String name, String status, long now) {
        if (name == null || status == null) return;
        String previous = statuses.get(name);
        if ("down".equals(status)) {
            aliveCandidates.remove(name);
            deadCandidates.remove(name);
            sidebarDownSeen.put(name, now);
            if (frCooldownRemainingMs(name, now) > 0L && !downSince.containsKey(name)) {
                // A stale DOWN row can survive briefly after a successful Fast Revive.
                statuses.put(name, "alive");
                return;
            }
            if (downSince.putIfAbsent(name, now) == null) totalDowns++;
            statuses.put(name, "down");
            return;
        }
        Long downAt = downSince.get(name);
        if ("dead".equals(status)) {
            aliveCandidates.remove(name);
            if (downAt != null && TeammateLifeStateRules.protectsActiveDown(
                    downAt, now, 25_000L)) {
                deadCandidates.remove(name);
                statuses.put(name, "down");
                return;
            }
            Long candidateSince = deadCandidates.putIfAbsent(name, now);
            if (candidateSince == null
                    || !TeammateLifeStateRules.shouldCommitDead(candidateSince, now)) {
                statuses.put(name, downAt == null ? "alive" : "down");
                return;
            }
            deadCandidates.remove(name);
            downSince.remove(name);
            statuses.put(name, "dead");
            if (!"dead".equals(previous)) totalDeaths++;
            return;
        }
        if ("quit".equals(status)) {
            aliveCandidates.remove(name);
            deadCandidates.remove(name);
            if (downAt != null && TeammateLifeStateRules.protectsActiveDown(
                    downAt, now, 25_000L)) {
                statuses.put(name, "down");
                return;
            }
            downSince.remove(name);
            statuses.put(name, "quit");
            return;
        }
        if ("alive".equals(status)) {
            deadCandidates.remove(name);
            if (downAt != null) {
                Long aliveSince = aliveCandidates.get(name);
                if (aliveSince == null) {
                    aliveCandidates.put(name, now);
                    statuses.put(name, "down");
                    return;
                }
                if (!TeammateLifeStateRules.shouldClearDown(downAt,
                        sidebarDownSeen.containsKey(name), aliveSince, now)) {
                    statuses.put(name, "down");
                    return;
                }
            }
            aliveCandidates.remove(name);
            sidebarDownSeen.remove(name);
            downSince.remove(name);
            statuses.put(name, "alive");
        }
    }

    public boolean consumeGameOver() {
        boolean pending = gameOverPending;
        gameOverPending = false;
        return pending;
    }

    private void clearLocalRescue() {
        localRescueTarget = null;
        localRescueModeMs = 0L;
        localRescueRemainingMs = 0L;
        localRescueObservedAt = 0L;
    }

    private void updateLocalRescue(ZombiesEventParser.Event event, long now) {
        if (event.subject() == null || event.subject().isBlank()) return;
        boolean newAttempt = localRescueTarget == null
                || !event.subject().equals(localRescueTarget)
                || localRescueModeMs <= 0L
                || event.actionbarRemainingMs() > localRescueRemainingMs + 150L
                || now - localRescueObservedAt > 750L;
        if (event.actionbarModeMs() > 0L && newAttempt) localRescueAttemptSeq++;
        localRescueTarget = event.subject();
        localRescueModeMs = Math.max(0L, event.actionbarModeMs());
        localRescueRemainingMs = Math.max(0L, event.actionbarRemainingMs());
        localRescueObservedAt = now;
    }

    private void setStatus(String name, String status) {
        if (name != null && status != null) statuses.put(name, status);
    }
}
