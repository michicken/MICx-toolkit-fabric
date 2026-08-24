package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;

/** Reads AA from its persistent sidebar area markers for the current client world. */
final class AntiAxeAaContext {

    private static volatile Object world;
    private static volatile boolean inAA;

    private AntiAxeAaContext() {
    }

    static void tick(Minecraft mc) {
        if (mc == null || mc.level == null) {
            clear();
            return;
        }
        if (world != mc.level) {
            world = mc.level;
            inAA = false;
        }
        ZombiesTracker tracker = ZombiesTracker.instance();
        if (!tracker.isInZombies()) {
            inAA = false;
            return;
        }
        // Area is persistent throughout AA, so do not retain an old-map positive result.
        inAA = tracker.isInAlienArcadium();
    }

    static boolean isInAA(Minecraft mc) {
        tick(mc);
        return inAA;
    }

    static void clear() {
        world = null;
        inAA = false;
    }
}
