package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 封包日志的离线回归：过滤规则、行格式、文件名。 */
class PacketLogRulesTest {

    @Test
    void outboundKeepsInteractionsAndDropsNoise() {
        // 买弹要看的就是这几条
        assertTrue(PacketLogRules.shouldLog(true, "ServerboundUseItemOnPacket", false));
        assertTrue(PacketLogRules.shouldLog(true, "ServerboundInteractPacket", false));
        assertTrue(PacketLogRules.shouldLog(true, "ServerboundUseItemPacket", false));
        assertTrue(PacketLogRules.shouldLog(true, "ServerboundContainerClickPacket", false));
        assertTrue(PacketLogRules.shouldLog(true, "ServerboundCustomPayloadPacket", false));
        assertTrue(PacketLogRules.shouldLog(true, "ServerboundChatCommandPacket", false));
        // 移动包一秒几十条，默认扔掉
        assertFalse(PacketLogRules.shouldLog(true, "ServerboundMovePlayerPacket", false));
        assertFalse(PacketLogRules.shouldLog(true, "ServerboundKeepAlivePacket", false));
        assertFalse(PacketLogRules.shouldLog(true, "ServerboundPlayerInputPacket", false));
    }

    @Test
    void inboundOnlyKeepsTheInterestingFew() {
        assertTrue(PacketLogRules.shouldLog(false, "ClientboundOpenScreenPacket", false));
        assertTrue(PacketLogRules.shouldLog(false, "ClientboundContainerSetContentPacket", false));
        assertTrue(PacketLogRules.shouldLog(false, "ClientboundContainerClosePacket", false));
        assertTrue(PacketLogRules.shouldLog(false, "ClientboundCustomPayloadPacket", false));
        assertTrue(PacketLogRules.shouldLog(false, "ClientboundSystemChatPacket", false));
        assertTrue(PacketLogRules.shouldLog(false, "ClientboundPlayerPositionPacket", false));
        // 入站噪声默认全丢
        assertFalse(PacketLogRules.shouldLog(false, "ClientboundSetTimePacket", false));
        assertFalse(PacketLogRules.shouldLog(false, "ClientboundLevelChunkWithLightPacket", false));
        assertFalse(PacketLogRules.shouldLog(false, "ClientboundSetEntityDataPacket", false));
    }

    @Test
    void logAllOverridesEveryFilter() {
        assertTrue(PacketLogRules.shouldLog(true, "ServerboundMovePlayerPacket", true));
        assertTrue(PacketLogRules.shouldLog(false, "ClientboundSetTimePacket", true));
        assertFalse(PacketLogRules.shouldLog(true, "", true));
        assertFalse(PacketLogRules.shouldLog(true, null, false));
    }

    @Test
    void lineCarriesDirectionAndOffset() {
        String out = PacketLogRules.line(1_000L, 13_345L, true, "UseItemOn(0,0,0)");
        assertTrue(out.contains("OUT"), out);
        assertTrue(out.contains("+   12.345s"), out);
        assertTrue(out.endsWith("UseItemOn(0,0,0)"), out);
        assertTrue(PacketLogRules.line(0L, 0L, false, "X").contains("IN"));
    }

    @Test
    void markerAndFileStampAreReadable() {
        long t = 1_700_000_000_000L;
        String marker = PacketLogRules.marker(0L, 60_000L, "按了买弹");
        assertTrue(marker.contains("按了买弹"), marker);
        assertTrue(marker.contains("+60.0s"), marker);
        assertTrue(PacketLogRules.fileName(t).startsWith("micx-packets-"));
        assertTrue(PacketLogRules.fileName(t).endsWith(".log"));
    }

    @Test
    void truncateFlattensAndCaps() {
        assertEquals("a b", PacketLogRules.truncate("a\nb", 10));
        assertEquals("abc…", PacketLogRules.truncate("abcdef", 3));
        assertEquals("", PacketLogRules.truncate(null, 5));
    }
}
