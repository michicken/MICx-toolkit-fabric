package dev.micx.micxfabric;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * RemoteShop（远程商店 / 远程买弹）的纯逻辑：目标关键词匹配、距离判定、触发冷却。
 *
 * <p>服务端硬上限（2026-09-15 逐 jar 查证）：
 * <b>1.8 引擎（Zombies 实际跑的那套）</b>右键实体走 {@code hasLineOfSight ? distSq<36 : distSq<9}
 * （6 格有视野 / 3 格隔墙）；右键方块由服务端按朝眼<b>重新 rayTrace 校验</b>（生存 4.5 / 创造 5.0 格）。
 * 26.2 原版是实体 6 格 / 方块 5.5 格。落在闸门外的包会被<b>静默丢弃</b>，插件连事件都收不到。
 *
 * <p>触发阈值取 4.9 格（用户定稿 2026-09-23）：实测 5.0 格就会被服务端拦截；方块型商店在 1.8 上
 * 只到 4.5 格，所以隔墙/超远都过不去——想更远只能换通道（先用 PacketLog 抓清楚它到底走哪条）。
 */
public final class RemoteShopRules {
    /** 服务端允许的最远实体交互距离（有视野）。 */
    public static final double SERVER_ENTITY_LIMIT = 6.0;
    /** 服务端允许的最远方块交互距离（1.8：服务端 rayTrace，生存模式 4.5 格）。 */
    public static final double SERVER_BLOCK_LIMIT = 4.5;
    /** 实际触发阈值（用户定稿 2026-09-23）：实测 5.0 格就会被拦截，收到 4.9。 */
    public static final double TRIGGER_LIMIT = 4.9;
    /** 客户端射程抬高到的值（原版准星只有实体 3 格 / 方块 4.5 格）。 */
    public static final double CLIENT_RANGE = 5.5;
    /**
     * 每把枪发包后的独立冷却（用户定稿 2026-09-23）：只在真正发出交互包后计时——
     * 连点没触发（超程/没目标）不进冷却，各枪互不共享。
     */
    public static final long BUY_COOLDOWN_MS = 800L;
    /** 扫描半径：够看到对面墙上的全息就行，不做全图扫。 */
    public static final double SCAN_RADIUS = 32.0;
    /** 默认关键词：Hypixel Zombies 的补弹台全息上写着 Refill / Ammo。 */
    public static final List<String> DEFAULT_KEYWORDS = List.of("refill", "ammo", "弹药", "补给");
    private static final int MAX_KEYWORDS = 12;

    private RemoteShopRules() {
    }

    /** 名字里含任一关键词（忽略大小写）即算目标。 */
    public static boolean matches(String name, List<String> keywords) {
        if (name == null || name.isBlank() || keywords == null || keywords.isEmpty()) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) continue;
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /** 面板里填的一串关键词（逗号/空格/顿号分隔）解析成小写去重列表；空则回落默认。 */
    public static List<String> parseKeywords(String raw) {
        LinkedHashSet<String> parsed = new LinkedHashSet<>();
        if (raw != null) {
            for (String part : raw.split("[,，、\\s|/]+")) {
                String keyword = part.trim().toLowerCase(Locale.ROOT);
                if (!keyword.isEmpty()) parsed.add(keyword);
                if (parsed.size() >= MAX_KEYWORDS) break;
            }
        }
        if (parsed.isEmpty()) return DEFAULT_KEYWORDS;
        return List.copyOf(new ArrayList<>(parsed));
    }

    /** 关键词列表还原成面板可编辑的一行文本。 */
    public static String keywordsText(List<String> keywords) {
        return keywords == null || keywords.isEmpty() ? "" : String.join(", ", keywords);
    }

    /** 距离是否够得着（服务端会接受这次交互）。 */
    public static boolean withinTriggerRange(double distance) {
        return distance >= 0.0 && distance <= TRIGGER_LIMIT;
    }

    /** 面板上的距离判语（按 1.8 闸门口径）。 */
    public static String distanceVerdict(double distance) {
        if (distance < 0.0 || Double.isNaN(distance)) return "无法测距";
        if (distance <= SERVER_BLOCK_LIMIT) return "闸门内（方块 4.5 / 实体 6 格）";
        if (distance <= SERVER_ENTITY_LIMIT) return "擦边（1.8 实体 6 格有视野，方块只到 4.5）";
        return "超出服务端闸门，原版通道会丢包";
    }
}
