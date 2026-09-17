package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerUpHudRulesTest {
    private static Map<String, PowerUpTimer.Active> actives(String... kindsAndSeconds) {
        Map<String, PowerUpTimer.Active> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kindsAndSeconds.length; i += 2) {
            String kind = kindsAndSeconds[i];
            long seconds = Long.parseLong(kindsAndSeconds[i + 1]);
            map.put(kind, new PowerUpTimer.Active(kind, 0L, seconds * 1000L));
        }
        return map;
    }

    @Test
    void bothActivePowerUpsAreShownWithTheirOwnRemaining() {
        // 用户报障 2026-09-17：两个 powerup 同时在计时时，旧的/短的那条以前直接不显示。
        Map<String, PowerUpTimer.Active> snap = actives("max", "35", "insta", "12");
        List<String> kinds = PowerUpHudRules.visibleKinds(snap, 1_000L);
        assertEquals(List.of("max", "insta"), kinds);

        String line = PowerUpHudRules.renderLine(snap, 1_000L);
        assertTrue(line.contains("Max Ammo:34.00s"), line);
        assertTrue(line.contains("Insta Kill:11.00s"), line);
        assertTrue(line.contains(PowerUpHudRules.SEPARATOR), line);
        // 两条都在同一行里，短的那条不会被长的顶掉
        assertTrue(line.indexOf("Max Ammo") < line.indexOf("Insta Kill"), line);
    }

    @Test
    void expiredEntriesAreSkippedAndEmptySnapshotDrawsNothing() {
        Map<String, PowerUpTimer.Active> snap = actives("max", "35", "insta", "12");
        // now = 15s：insta（12s 到点）已过期 → 只剩 max
        assertEquals(List.of("max"), PowerUpHudRules.visibleKinds(snap, 15_000L));
        String line = PowerUpHudRules.renderLine(snap, 15_000L);
        assertTrue(line.contains("Max Ammo:20.00s"), line);
        assertFalse(line.contains("Insta Kill"), line);
        // 全部过期 → 空串（渲染层画占位）
        assertEquals("", PowerUpHudRules.renderLine(snap, 40_000L));
        assertEquals("", PowerUpHudRules.renderLine(null, 1_000L));
        assertEquals("", PowerUpHudRules.line(List.of()));
    }

    @Test
    void segmentUsesTwoDecimalsLikeTheForgeVersion() {
        // 1.8.9：puColorLabel(kind) + ":" + String.format("%.2f", rem/1000.0) + "s"
        assertEquals("§cInsta Kill:12.34s", PowerUpHudRules.segment("insta", 12_340L));
        assertEquals("§9Max Ammo:0.50s", PowerUpHudRules.segment("max", 500L));
        // 负数（已过期但还没被清理）夹到 0
        assertEquals("§6Double Gold:0.00s", PowerUpHudRules.segment("dg", -5L));
    }

    @Test
    void canonicalKindMergesParserVariantsAndKeepsUnknownKinds() {
        assertEquals("insta", PowerUpHudRules.canonicalKind("INSTA KILL"));
        assertEquals("insta", PowerUpHudRules.canonicalKind("Insta Kill"));
        assertEquals("max", PowerUpHudRules.canonicalKind("Max Ammo"));
        assertEquals("dg", PowerUpHudRules.canonicalKind("Double Gold"));
        assertEquals("bg", PowerUpHudRules.canonicalKind("Bonus Gold"));
        assertEquals("shopping", PowerUpHudRules.canonicalKind("Shopping Spree"));
        assertEquals("carp", PowerUpHudRules.canonicalKind("Carpenter"));
        assertEquals("weird thing", PowerUpHudRules.canonicalKind("Weird Thing"));
        assertEquals("", PowerUpHudRules.canonicalKind(null));
        // 段色名与 1.8.9 puColorLabel 对齐；未知名字给灰底原样名，空名字给占位
        assertEquals("§cInsta Kill", PowerUpHudRules.label("INSTA KILL"));
        assertEquals("§7Power-up", PowerUpHudRules.label("   "));
        assertEquals("§7Weird Thing", PowerUpHudRules.label("Weird Thing"));
    }
}
