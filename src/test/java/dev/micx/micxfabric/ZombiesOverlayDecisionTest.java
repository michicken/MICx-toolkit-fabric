package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZombiesOverlayDecisionTest {
    @Test
    void hidesBossBarOnlyInsideOverlaySession() {
        assertTrue(ZombiesOverlayDecision.hideBossBar(true, true, true));
        assertFalse(ZombiesOverlayDecision.hideBossBar(false, true, true));
        assertFalse(ZombiesOverlayDecision.hideBossBar(true, false, true));
        assertFalse(ZombiesOverlayDecision.hideBossBar(true, true, false));
    }

    @Test
    void hidesSidebarOnlyWhenReplacementPanelIsOwned() {
        assertTrue(ZombiesOverlayDecision.hideSidebar(true, true, true, false, true));
        assertFalse(ZombiesOverlayDecision.hideSidebar(true, true, true, true, true));
        assertFalse(ZombiesOverlayDecision.hideSidebar(true, true, true, false, false));
        assertFalse(ZombiesOverlayDecision.hideSidebar(true, false, true, false, true));
    }
}
