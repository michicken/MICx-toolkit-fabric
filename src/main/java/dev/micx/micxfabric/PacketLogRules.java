package dev.micx.micxfabric;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * PacketLog（操作/封包日志）的纯逻辑：过滤规则、行格式、文件名。
 *
 * <p>目标很具体：<b>手动买一次子弹</b>，然后在日志里看清"这件事到底走了哪条通道"——
 * 右键方块（UseItemOn）？右键实体（Interact）？右键空气（UseItem）？点界面格子（ContainerClick）？
 * 还是自定义通道（CustomPayload）？以及服务端回了什么（开界面/聊天/标题/音效）。
 * 所以默认只记"交互 + 界面 + 聊天 + 通道"这几类，移动包这种高频噪声一律扔掉。
 */
public final class PacketLogRules {
    /** 出站噪声：默认不记（移动/心跳/输入状态这种一秒几十条的）。 */
    private static final List<String> OUTBOUND_IGNORED = List.of(
            "ServerboundMovePlayerPacket",
            "ServerboundMoveVehiclePacket",
            "ServerboundPlayerInputPacket",
            "ServerboundKeepAlivePacket",
            "ServerboundClientTickEndPacket",
            "ServerboundPongPacket",
            "ServerboundClientInformationPacket");

    /** 入站白名单：只记这几类（其余一秒几十条，记了也看不出东西）。 */
    private static final List<String> INBOUND_KEPT = List.of(
            "ClientboundOpenScreenPacket",
            "ClientboundContainerSetContentPacket",
            "ClientboundContainerSetSlotPacket",
            "ClientboundContainerClosePacket",
            "ClientboundSystemChatPacket",
            "ClientboundCustomPayloadPacket",
            "ClientboundSetTitleTextPacket",
            "ClientboundSetSubtitleTextPacket",
            "ClientboundSetActionBarTextPacket",
            "ClientboundPlayerPositionPacket",
            "ClientboundSoundPacket",
            "ClientboundSetScorePacket",
            "ClientboundSetDisplayObjectivePacket",
            "ClientboundAddEntityPacket",
            "ClientboundRespawnPacket",
            "ClientboundDisconnectPacket");

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private PacketLogRules() {
    }

    /** 这条包该不该记。logAll=true 时无差别全记（调试用，日志会很大）。 */
    public static boolean shouldLog(boolean outbound, String simpleName, boolean logAll) {
        if (simpleName == null || simpleName.isBlank()) return false;
        if (logAll) return true;
        return outbound ? !contains(OUTBOUND_IGNORED, simpleName) : contains(INBOUND_KEPT, simpleName);
    }

    private static boolean contains(List<String> names, String simpleName) {
        for (String name : names) {
            if (name.equals(simpleName)) return true;
        }
        return false;
    }

    /** 一行日志：`[+  12.345s] OUT ServerboundUseItemOnPacket(...)`。 */
    public static String line(long sessionStartMs, long nowMs, boolean outbound, String rendered) {
        return String.format(Locale.ROOT, "[+%9.3fs] %-3s %s",
                (nowMs - sessionStartMs) / 1000.0D, outbound ? "OUT" : "IN", rendered);
    }

    /** 标记线：方便在长日志里按段看（快捷键手动打点）。 */
    public static String marker(long sessionStartMs, long nowMs, String label) {
        return String.format(Locale.ROOT, "---------- %s | 会话 +%.1fs | %s ----------",
                label == null ? "标记" : label, (nowMs - sessionStartMs) / 1000.0D, wallClock(nowMs));
    }

    /** 会话开头的头信息。 */
    public static String header(long nowMs, String minecraftVersion) {
        return "# MICx 封包日志 | " + wallClock(nowMs) + " | MC " + minecraftVersion
                + " | 出站只记交互/聊天/通道，入站只记界面/聊天/标题/通道/拉回";
    }

    /** 日志文件名（按启用时刻命名，一次会话一个文件）。 */
    public static String fileName(long nowMs) {
        return "micx-packets-" + STAMP.format(Instant.ofEpochMilli(nowMs)) + ".log";
    }

    public static String wallClock(long epochMs) {
        return STAMP.format(Instant.ofEpochMilli(epochMs));
    }

    /** 超长的行掐掉，免得整条聊天或道具清单把日志撑爆。 */
    public static String truncate(String value, int max) {
        if (value == null) return "";
        String flat = value.replace('\n', ' ').replace('\r', ' ').trim();
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }
}
