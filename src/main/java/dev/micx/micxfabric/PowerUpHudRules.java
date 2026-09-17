package dev.micx.micxfabric;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 顶部 HUD「Power Up 剩余倒计时」的纯规则层 —— 对齐 1.8.9（用户报障 2026-09-17）。
 *
 * <p>1.8.9 的 {@code ZombiesAssistModule.renderPuCountdownLine} 把 {@code puActive} 里
 * <b>所有</b>未过期条目画在屏幕中央同一行，用 {@code " §7| "} 分隔，每条格式为
 * {@code <段色名>:<两位小数>s}（精确到 0.01s，每帧重算）。26.2 之前只画「剩余最长的那一条」，
 * 于是两个 powerup 同时生效时旧的/短的那个直接不显示，看起来像被删掉。
 *
 * <p>这里只放「取哪些条目、怎么拼」的纯逻辑，渲染层负责画。
 */
public final class PowerUpHudRules {
    /** 条目分隔（1.8.9 同款：灰色竖线 + 两侧空格）。 */
    public static final String SEPARATOR = " §7| ";

    private PowerUpHudRules() {
    }

    /**
     * 同一 powerup 的规范名：解析器聊天路径给 {@code INSTA KILL}、字幕路径给 {@code Insta Kill}，
     * 不归一化就会在计时表里留下两条同名条目（1.8.9 是按 kind 单条的）。未知名字返回小写原值。
     */
    public static String canonicalKind(String kind) {
        String value = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        if (value.contains("max ammo") || value.equals("max")) return "max";
        if (value.contains("insta") || value.contains("instant kill") || value.equals("ins")) {
            return "insta";
        }
        if (value.contains("shopping") || value.equals("ss")) return "shopping";
        if (value.contains("double gold") || value.equals("dg")) return "dg";
        if (value.contains("bonus gold") || value.equals("bg")) return "bg";
        if (value.contains("carp")) return "carp";
        return value;
    }

    /** 顶部 HUD 显示用的段色名（§ 段色与 1.8.9 的 {@code puColorLabel} 同色）。 */
    public static String label(String kind) {
        return switch (canonicalKind(kind)) {
            case "max" -> "§9Max Ammo";
            case "insta" -> "§cInsta Kill";
            case "shopping" -> "§5Shopping Spree";
            case "dg" -> "§6Double Gold";
            case "bg" -> "§6Bonus Gold";
            case "carp" -> "§eCarpenter";
            default -> {
                String raw = kind == null ? "" : kind.trim();
                yield raw.isEmpty() ? "§7Power-up" : "§7" + raw;
            }
        };
    }

    /**
     * 本帧要显示的 powerup：<b>全部未过期</b>的条目，按激活顺序（{@code active} 是 LinkedHashMap，
     * 先激活的在前）。1.8.9 是遍历整个 map 逐个跳过过期项，这里是同一口径的确定性版本。
     */
    public static List<String> visibleKinds(Map<String, PowerUpTimer.Active> snapshot, long now) {
        List<String> kinds = new ArrayList<>();
        if (snapshot == null || snapshot.isEmpty()) return kinds;
        for (Map.Entry<String, PowerUpTimer.Active> entry : snapshot.entrySet()) {
            PowerUpTimer.Active active = entry.getValue();
            if (active == null || active.expiresAt() - now <= 0L) continue;
            kinds.add(entry.getKey());
        }
        return kinds;
    }

    /** 单条片段：{@code <段色名>:<两位小数>s}（1.8.9 同款精确到 0.01s）。 */
    public static String segment(String kind, long remainingMs) {
        return label(kind) + ":" + String.format(Locale.ROOT, "%.2f", Math.max(0L, remainingMs) / 1000.0)
                + "s";
    }

    /** 把片段拼成一行；空列表返回空串（调用方自行画占位）。 */
    public static String line(List<String> segments) {
        if (segments == null || segments.isEmpty()) return "";
        return String.join(SEPARATOR, segments);
    }

    /** 一键取当前要画的那一行（无活动 powerup 返回空串）。 */
    public static String renderLine(Map<String, PowerUpTimer.Active> snapshot, long now) {
        List<String> segments = new ArrayList<>();
        for (String kind : visibleKinds(snapshot, now)) {
            PowerUpTimer.Active active = snapshot.get(kind);
            segments.add(segment(kind, active.expiresAt() - now));
        }
        return line(segments);
    }
}
