package dev.micx.micxfabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 双基准：A（查找顺序见 {@link #candidates}，全部落空时用打包资源 /baseline/aa105.json = 11015 兜底），
 * B（同序，兜底 /baseline/aa105_10905.json = 10905）。
 */
public final class SpeedrunBaseline {
    private static final SpeedrunBaseline INSTANCE = new SpeedrunBaseline();
    public static SpeedrunBaseline get() { return INSTANCE; }

    static final class Data {
        final long[] perRoundMs;
        final long[] cumulativeMs;
        final long totalMs;
        final String source;
        final String label;
        Data(long[] p, long[] c, long total, String source, String label) {
            this.perRoundMs = p; this.cumulativeMs = c; this.totalMs = total; this.source = source; this.label = label;
        }
        int rounds() { return perRoundMs == null ? 0 : perRoundMs.length; }
        boolean has() { return perRoundMs != null && perRoundMs.length > 0; }
        long perRound(int round) { if (perRoundMs == null || round < 1 || round > perRoundMs.length) return -1; return perRoundMs[round - 1]; }
        long cumulative(int round) { if (cumulativeMs == null || round < 1 || round > cumulativeMs.length) return -1; return cumulativeMs[round - 1]; }
    }

    private volatile Data a = null;
    private volatile Data b = null;
    private volatile boolean loaded = false;

    private SpeedrunBaseline() {}

    public synchronized void load() {
        if (loaded) return;
        loaded = true;
        a = loadSlot("baseline.json", "MICxToolkit_baseline.json", "/baseline/aa105.json", "11015");
        b = loadSlot("baseline2.json", "MICxToolkit_baseline_2.json", "/baseline/aa105_10905.json", "10905");
    }

    /**
     * 基线文件的查找顺序（相对 {@code <gameDir>/config}，先命中先用）。
     *
     * <p>用户定稿 2026-09-16：0.2.86 把 10832（1:08:45 的个人记录）放在 {@code config/baseline.json}
     * ——游戏 config 根目录；而当时的实现只找 {@code config/MICxToolkit/} 与 Forge 风格的
     * {@code MICxToolkit_baseline*.json}，两处都落空后静默回退到打包资源里的旧基线（11015），
     * 于是「记录一直没显示」。现在三个位置都找，模块自己的目录优先。
     */
    static java.util.List<String> candidates(String configName, String legacyName) {
        return java.util.List.of("MICxToolkit/" + configName, configName, legacyName);
    }

    private Data loadSlot(String configName, String legacyName, String resource, String defaultLabel) {
        try {
            JsonObject obj = null;
            String srcLabel = null;
            Path configDir = FabricRuntime.configPath();
            Path configRoot = configDir.getParent() == null ? configDir : configDir.getParent();
            File f = null;
            for (String relative : candidates(configName, legacyName)) {
                Path candidate = configRoot.resolve(relative);
                if (Files.isRegularFile(candidate)) {
                    f = candidate.toFile();
                    break;
                }
            }
            if (f != null && f.exists()) {
                obj = JsonParser.parseReader(new FileReader(f, StandardCharsets.UTF_8)).getAsJsonObject();
                srcLabel = f.getName();
            } else {
                try (InputStream is = getClass().getResourceAsStream(resource)) {
                    if (is != null) {
                        obj = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
                        srcLabel = resource;
                    }
                }
            }
            if (obj == null) return null;
            JsonArray per = obj.getAsJsonArray("perRoundMs");
            JsonArray cum = obj.getAsJsonArray("cumulativeMs");
            if (per == null || cum == null) return null;
            int n = per.size();
            long[] p = new long[n];
            long[] c = new long[n];
            for (int i = 0; i < n; i++) p[i] = per.get(i).getAsLong();
            for (int i = 0; i < n; i++) c[i] = cum.get(i).getAsLong();
            long total = obj.has("totalMs") ? obj.get("totalMs").getAsLong() : (n > 0 ? c[n - 1] : 0L);
            String label = obj.has("label") && !obj.get("label").isJsonNull()
                    ? obj.get("label").getAsString() : defaultLabel;
            return new Data(p, c, total, srcLabel, label);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---- Slot A（11015，保留原 API 供既有调用点）----
    public boolean hasBaseline() { load(); return a != null && a.has(); }
    public long baselineMs(int round) { load(); return a == null ? -1 : a.perRound(round); }
    public long cumulativeBaselineMs(int round) { load(); return a == null ? -1 : a.cumulative(round); }
    public int rounds() { load(); return a == null ? 0 : a.rounds(); }
    public String source() { load(); return a == null ? null : a.source; }
    public String labelA() { load(); return a == null ? "11015" : a.label; }

    // ---- Slot B（10905）----
    public boolean hasBaselineB() { load(); return b != null && b.has(); }
    public long baselineMsB(int round) { load(); return b == null ? -1 : b.perRound(round); }
    public long cumulativeBaselineMsB(int round) { load(); return b == null ? -1 : b.cumulative(round); }
    public int roundsB() { load(); return b == null ? 0 : b.rounds(); }
    public String labelB() { load(); return b == null ? "10905" : b.label; }

    public static String formatDelta(long deltaMs) {
        boolean ahead = deltaMs < 0;
        long abs = Math.abs(deltaMs);
        String s = String.format(java.util.Locale.ROOT, "%.1fs", abs / 1000.0);
        return (ahead ? "-" : "+") + s;
    }
    public static int deltaColor(long deltaMs) {
        if (deltaMs < 0) return 0xFF55FF55;
        if (deltaMs > 0) return 0xFFFF5555;
        return 0xFFAAAAAA;
    }
}
