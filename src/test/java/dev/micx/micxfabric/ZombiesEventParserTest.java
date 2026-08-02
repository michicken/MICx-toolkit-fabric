package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZombiesEventParserTest {
    @Test
    void parsesCombatAndLocalReviveMessages() {
        ZombiesEventParser.Event knock = ZombiesEventParser.parseChat("Alice was knocked down");
        assertEquals(ZombiesEventParser.Kind.KNOCK, knock.kind());
        assertEquals("Alice", knock.subject());

        ZombiesEventParser.Event revivedYou = ZombiesEventParser.parseChat("Bob revived you");
        assertEquals(ZombiesEventParser.Kind.REVIVE, revivedYou.kind());
        assertEquals("you", revivedYou.subject());
        assertEquals("Bob", revivedYou.actor());

        ZombiesEventParser.Event youRevived = ZombiesEventParser.parseChat("You revived Alice");
        assertEquals("Alice", youRevived.subject());
        assertEquals("you", youRevived.actor());
    }

    @Test
    void parsesFastReviveActionbarState() {
        ZombiesEventParser.Event hold = ZombiesEventParser.parseActionBar(
                "Hold SNEAK to revive Alice.");
        assertEquals(ZombiesEventParser.Kind.FAST_REVIVE, hold.kind());
        assertEquals("Alice", hold.subject());
        assertEquals(0L, hold.actionbarModeMs());

        ZombiesEventParser.Event reviving = ZombiesEventParser.parseActionBar(
                "Reviving Alice - 1.2s");
        assertEquals(ZombiesEventParser.Kind.FAST_REVIVE, reviving.kind());
        assertEquals(1_200L, reviving.actionbarRemainingMs());
        assertEquals(1_500L, reviving.actionbarModeMs());
    }

    @Test
    void titleOnlyHandlesRoundAndSubtitleOnlyKnownPowerups() {
        assertEquals(ZombiesEventParser.Kind.ROUND,
                ZombiesEventParser.parseTitle("§6Round 21").kind());
        assertEquals(ZombiesEventParser.Kind.UNKNOWN,
                ZombiesEventParser.parseTitle("Game Over").kind());
        assertEquals(ZombiesEventParser.Kind.POWERUP_ACTIVATED,
                ZombiesEventParser.parseSubtitle("Double Gold").kind());
        assertEquals(ZombiesEventParser.Kind.UNKNOWN,
                ZombiesEventParser.parseSubtitle("Mystery Powerup").kind());
        assertEquals(ZombiesEventParser.Kind.UNKNOWN,
                ZombiesEventParser.parseSubtitle("Mystery Powerup activated for 20s").kind());
    }

    @Test
    void chatEventsAcceptForgeStylePrefixesButMalformedReviveDoesNotConsumeAmmo() {
        assertEquals(ZombiesEventParser.Kind.KNOCK,
                ZombiesEventParser.parseChat("[Zombies] §cAlice was knocked down").kind());
        assertEquals(ZombiesEventParser.Kind.REVIVE,
                ZombiesEventParser.parseChat("[C] Bob revived Alice").kind());
        assertEquals(ZombiesEventParser.Kind.KILLED,
                ZombiesEventParser.parseChat("[Zombies] Alice was slain").kind());
        assertEquals(ZombiesEventParser.Kind.UNKNOWN,
                ZombiesEventParser.parseActionBar("REVIVING").kind());
        assertEquals(ZombiesEventParser.Kind.RELOADING,
                ZombiesEventParser.parseActionBar("REVIVING... RELOADING").kind());
    }

    @Test
    void parsesPowerupActionbarAndGameOverSignals() {
        ZombiesEventParser.Event powerup = ZombiesEventParser.parseSubtitle("Shopping Spree");
        assertEquals(ZombiesEventParser.Kind.POWERUP_ACTIVATED, powerup.kind());
        assertEquals(20, powerup.durationSeconds());

        assertEquals(ZombiesEventParser.Kind.RELOADING,
                ZombiesEventParser.parseActionBar("RELOADING").kind());
        assertEquals(ZombiesEventParser.Kind.OUT_OF_AMMO,
                ZombiesEventParser.parseActionBar("OUT OF AMMO").kind());
        assertEquals(ZombiesEventParser.Kind.GAME_OVER,
                ZombiesEventParser.parseChat("Game Over").kind());
    }
}
