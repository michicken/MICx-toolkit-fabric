package dev.micx.micxfabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 服务端 {@code version.json} 的解析结果。
 *
 * <p>这是从网络拿回来的数据，一律防御式解析：字段缺失、类型不对、结构不像预期，都只记进
 * {@link #problems()} 而不抛异常——更新是后台行为，解析失败只应该导致「这次不更新」。
 *
 * <p>{@code files} 段是自动更新新加的，老客户端不认这个字段会自动忽略，所以服务端可以
 * 一次把两边的信息都写进去。
 */
public final class UpdateManifest {
    /** 26.2 Fabric 版在 files 段里的键。 */
    public static final String PLATFORM_FABRIC = "fabric-26.2";

    /** 一条可下载的文件描述。 */
    public record Entry(String fileName, String url, String sha256, long size) {
        /** 四个字段齐了才算一条能用的记录。 */
        public boolean complete() {
            return fileName != null && !fileName.isBlank()
                    && url != null && !url.isBlank()
                    && UpdateRules.isSha256(sha256)
                    && size > 0;
        }
    }

    private final String version;
    private final long publishedAtMs;
    private final long delayMs;
    private final Entry entry;
    private final List<String> problems;

    private UpdateManifest(String version, long publishedAtMs, long delayMs, Entry entry, List<String> problems) {
        this.version = version;
        this.publishedAtMs = publishedAtMs;
        this.delayMs = delayMs;
        this.entry = entry;
        this.problems = Collections.unmodifiableList(problems);
    }

    /**
     * 解析清单。{@code platformKey} 是本平台在 {@code files} 里的键，缺省用
     * {@link #PLATFORM_FABRIC}；给同域其它产品留了复用口子。
     */
    public static UpdateManifest parse(String json, String platformKey) {
        List<String> problems = new ArrayList<>();
        String version = null;
        long publishedAtMs = 0L;
        long delayMs = UpdateRules.DEFAULT_DELAY_MS;
        Entry entry = null;

        JsonObject root = null;
        if (json == null || json.isBlank()) {
            problems.add("清单为空");
        } else {
            try {
                JsonElement parsed = JsonParser.parseString(json);
                if (parsed != null && parsed.isJsonObject()) root = parsed.getAsJsonObject();
                else problems.add("清单不是 JSON 对象");
            } catch (RuntimeException exception) {
                problems.add("清单不是合法 JSON");
            }
        }

        if (root != null) {
            version = string(root, "version", problems);
            publishedAtMs = instant(root, "publishedAt", problems);
            delayMs = minutes(root, "installDelayMinutes", UpdateRules.DEFAULT_DELAY_MS, problems);
            entry = entry(root, platformKey == null ? PLATFORM_FABRIC : platformKey, problems);
        }
        return new UpdateManifest(version, publishedAtMs, delayMs, entry, problems);
    }

    /** 远端版本号；缺失返回 null。 */
    public String version() {
        return version;
    }

    /** 发布时刻（毫秒）；缺失或解析失败返回 0，语义是「立即生效」。 */
    public long publishedAtMs() {
        return publishedAtMs;
    }

    /** 发布后多久才允许安装。 */
    public long delayMs() {
        return delayMs;
    }

    /** 本平台的下载记录；没有或残缺时返回 null。 */
    public Entry entry() {
        return entry;
    }

    /** 版本号与下载记录都齐了，这次更新才有得做。 */
    public boolean usable() {
        return version != null && !version.isBlank() && entry != null && entry.complete();
    }

    /** 解析过程中遇到的问题（空表示一切正常）。 */
    public List<String> problems() {
        return problems;
    }

    /** 把相对 URL 拼到站点上。绝对 URL 原样返回。 */
    public String absoluteUrl(String base) {
        if (entry == null) return null;
        String url = entry.url();
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        String root = base == null ? "" : base.trim();
        while (root.endsWith("/")) root = root.substring(0, root.length() - 1);
        return root + (url.startsWith("/") ? url : "/" + url);
    }

    private static String string(JsonObject root, String key, List<String> problems) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            problems.add("缺 " + key);
            return null;
        }
        try {
            String value = element.getAsString();
            if (value == null || value.isBlank()) {
                problems.add(key + " 为空");
                return null;
            }
            return value.trim();
        } catch (RuntimeException exception) {
            problems.add(key + " 不是字符串");
            return null;
        }
    }

    private static long instant(JsonObject root, String key, List<String> problems) {
        String raw = string(root, key, new ArrayList<>());
        if (raw == null) return 0L;
        try {
            return Instant.parse(raw).toEpochMilli();
        } catch (RuntimeException exception) {
            problems.add(key + " 不是 ISO-8601 时间，按立即生效处理");
            return 0L;
        }
    }

    private static long minutes(JsonObject root, String key, long fallbackMs, List<String> problems) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) return fallbackMs;
        try {
            long minutes = element.getAsLong();
            if (minutes < 0) {
                problems.add(key + " 为负，改用默认值");
                return fallbackMs;
            }
            return minutes * 60_000L;
        } catch (RuntimeException exception) {
            problems.add(key + " 不是整数，改用默认值");
            return fallbackMs;
        }
    }

    private static Entry entry(JsonObject root, String platformKey, List<String> problems) {
        JsonElement filesElement = root.get("files");
        if (filesElement == null || !filesElement.isJsonObject()) {
            problems.add("缺 files 段（服务端还没提供下载地址）");
            return null;
        }
        JsonObject files = filesElement.getAsJsonObject();
        JsonElement itemElement = files.get(platformKey);
        if (itemElement == null || !itemElement.isJsonObject()) {
            problems.add("files 里没有 " + platformKey);
            return null;
        }
        JsonObject item = itemElement.getAsJsonObject();
        String fileName = string(item, "fileName", problems);
        String url = string(item, "url", problems);
        String sha256 = string(item, "sha256", problems);
        long size = 0L;
        JsonElement sizeElement = item.get("size");
        if (sizeElement == null || sizeElement.isJsonNull()) {
            problems.add("缺 size");
        } else {
            try {
                size = sizeElement.getAsLong();
            } catch (RuntimeException exception) {
                problems.add("size 不是整数");
            }
        }
        Entry entry = new Entry(fileName, url, sha256, size);
        if (!entry.complete()) problems.add("files." + platformKey + " 字段不全，不能用");
        return entry;
    }
}
