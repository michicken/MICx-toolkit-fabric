package dev.micx.micxfabric;

/** Minecraft-free visibility contract shared by custom HUD and vanilla HUD hooks. */
public final class ZombiesOverlayDecision {
    private ZombiesOverlayDecision() {
    }

    public static boolean hideBossBar(boolean moduleEnabled, boolean overlayEnabled, boolean inZombies) {
        return moduleEnabled && overlayEnabled && inZombies;
    }

    public static boolean hideSidebar(boolean moduleEnabled, boolean overlayEnabled, boolean inZombies,
                               boolean originalScoreboard, boolean showEconomy) {
        return moduleEnabled && overlayEnabled && inZombies && !originalScoreboard && showEconomy;
    }
}
