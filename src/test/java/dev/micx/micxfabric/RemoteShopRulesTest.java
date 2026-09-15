package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 远程商店的离线回归：关键词匹配、距离判定、冷却。 */
class RemoteShopRulesTest {

    @Test
    void matchesIsCaseInsensitiveAndSubstring() {
        assertTrue(RemoteShopRules.matches("[右键] Refill Ammo", RemoteShopRules.DEFAULT_KEYWORDS));
        assertTrue(RemoteShopRules.matches("REFILL AMMO 500", RemoteShopRules.DEFAULT_KEYWORDS));
        assertTrue(RemoteShopRules.matches("弹药补给站", RemoteShopRules.DEFAULT_KEYWORDS));
        assertFalse(RemoteShopRules.matches("Perk Machine", RemoteShopRules.DEFAULT_KEYWORDS));
        assertFalse(RemoteShopRules.matches(null, RemoteShopRules.DEFAULT_KEYWORDS));
        assertFalse(RemoteShopRules.matches("Refill Ammo", java.util.List.of()));
    }

    @Test
    void parseKeywordsSplitsDedupesAndFallsBack() {
        assertEquals(java.util.List.of("refill", "ammo"), RemoteShopRules.parseKeywords("Refill, AMMO"));
        assertEquals(java.util.List.of("refill", "ammo"), RemoteShopRules.parseKeywords("refill ammo refill"));
        assertEquals(java.util.List.of("补弹", "弹药"), RemoteShopRules.parseKeywords("补弹、弹药"));
        // 空 / 全分隔符 → 回落默认，不会变成「什么都匹配」
        assertEquals(RemoteShopRules.DEFAULT_KEYWORDS, RemoteShopRules.parseKeywords(""));
        assertEquals(RemoteShopRules.DEFAULT_KEYWORDS, RemoteShopRules.parseKeywords(" , 、 "));
        assertEquals("refill, ammo", RemoteShopRules.keywordsText(java.util.List.of("refill", "ammo")));
    }

    @Test
    void rangeLimitSitsBelowTheServerCeiling() {
        assertTrue(RemoteShopRules.TRIGGER_LIMIT <= RemoteShopRules.SERVER_ENTITY_LIMIT);
        // 触发阈值贴着实体闸门，但比方块闸门（1.8 rayTrace 4.5）松——方块型商店在 1.8 上摸不到 5 格
        assertTrue(RemoteShopRules.TRIGGER_LIMIT <= RemoteShopRules.SERVER_ENTITY_LIMIT);
        assertTrue(RemoteShopRules.TRIGGER_LIMIT > RemoteShopRules.SERVER_BLOCK_LIMIT);
        assertTrue(RemoteShopRules.withinTriggerRange(5.5));
        assertFalse(RemoteShopRules.withinTriggerRange(5.5001));
        assertFalse(RemoteShopRules.withinTriggerRange(-1.0));
    }

    @Test
    void verdictTellsTheTruthAboutDistance() {
        assertEquals("闸门内（方块 4.5 / 实体 6 格）", RemoteShopRules.distanceVerdict(3.0));
        assertEquals("闸门内（方块 4.5 / 实体 6 格）", RemoteShopRules.distanceVerdict(4.5));
        assertEquals("擦边（1.8 实体 6 格有视野，方块只到 4.5）", RemoteShopRules.distanceVerdict(5.8));
        assertEquals("超出服务端闸门，原版通道会丢包", RemoteShopRules.distanceVerdict(9.3));
    }

    @Test
    void triggerHonoursCooldownAndBackwardsClock() {
        assertTrue(RemoteShopRules.due(1_000L, -1L));
        assertFalse(RemoteShopRules.due(1_500L, 1_000L));
        assertTrue(RemoteShopRules.due(2_000L, 1_000L));
        // 时钟往回改：不该卡死到永远点不动
        assertTrue(RemoteShopRules.due(500L, 10_000L));
    }
}
