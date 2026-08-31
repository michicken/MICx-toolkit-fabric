package dev.micx.micxfabric;

/** Bridges POWERUP_ACTIVATED chat events into ZeSpawnOrderTracker.puRounds latch. */
final class ZombiesPuRoundsLatch {
    private ZombiesPuRoundsLatch() {}

    static void onPowerup(String kind, long now) {
        ZombiesTracker tracker = ZombiesTracker.instance();
        int round = tracker.round();
        if (round <= 0) return;
        long elapsed = tracker.roundStartMs() > 0 ? now - tracker.roundStartMs() : 0;
        WaveTable.ZbMap map = null;
        try {
            String t = tracker.frame().map();
            if (t != null) map = WaveTable.detect(t);
            if (map == null) { String st = tracker.sidebarTitle(); if (st != null) map = WaveTable.detect(st); }
        } catch (Throwable ignored) {}
        // AA SS also latches here (reuse same elapsed)
        ZombiesExplorerModule.instance().onPowerupActivated(kind, round, elapsed);
        // Back-compat: if someone later queries ZombiesPuRounds directly, keep it in sync
        // (currently only ZE tracker owns it, so this is sufficient)
        _unused(map);
    }

    private static void _unused(Object o) {}
}
