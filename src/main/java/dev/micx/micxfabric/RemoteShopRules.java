package dev.micx.micxfabric;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * RemoteShop（远程商店 / 远程买弹）的纯逻辑：目标关键词匹配、距离判定、触发冷却。
 *
 * <p>服务端硬上限（26.2 原版字节码实测，2026-09-15）：眼球到实体碰撞箱 &lt; (3.0+3.0)² = 6 格，
 * 到方块 &lt; (4.5+1.0)² = 5.5 格；超出服务端直接丢包、插件收不到事件——所以「远程」的天花板
 * 就是这里，客户端再改也过不去。
 *
 * <p>触发阈值取 5.5 格：留半格给移动与延迟，避免擦边丢包还以为是模块坏了。
 */
public final class RemoteShopRules {
    /** 服务端允许的最远实体交互距离（眼睛到碰撞箱）。 */
    public static final double SERVER_ENTITY_LIMIT = 6.0;
    /** 服务端允许的最远方块交互距离。 */
    public static final double SERVER_BLOCK_LIMIT = 5.5;
    /** 实际触发阈值：比服务端上限再收半格。 */
    public static final double TRIGGER_LIMIT = 5.5;
    /** 客户端射程抬高到的值（原版准星只有实体 3 格 / 方块 4.5 格）。 */
    public static final double CLIENT_RANGE = 5.5;
    /** 两次触发之间的最小间隔，别把机器点成连点。 */
    public static final long TRIGGER_COOLDOWN_MS = 1_000L;
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

    /** 面板上的距离判语。 */
    public static String distanceVerdict(double distance) {
        if (distance < 0.0 || Double.isNaN(distance)) return "无法测距";
        if (distance <= TRIGGER_LIMIT) return "射程内";
        if (distance <= SERVER_ENTITY_LIMIT) return "擦边（服务端上限 6 格）";
        return "超出服务端上限，发了也会被丢包";
    }

    /** 冷却是否走完（时钟被往回改时别卡死）。 */
    public static boolean due(long nowMs, long lastTriggerMs) {
        if (lastTriggerMs < 0L) return true;
        long elapsed = nowMs - lastTriggerMs;
        if (elapsed < 0L) return true;
        return elapsed >= TRIGGER_COOLDOWN_MS;
    }
}
