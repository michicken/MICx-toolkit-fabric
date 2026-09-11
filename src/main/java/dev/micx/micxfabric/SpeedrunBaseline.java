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

/** 双基准：A=11015（config 优先/打包 aa105.json 兜底），B=10905（config baseline2.json 优先/打包 aa105_10905.json 兜底）。 */
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

    private Data loadSlot(String configName, String legacyName, String resource, String defaultLabel) {
        try {
            JsonObject obj = null;
            String srcLabel = null;
            Path configDir = FabricRuntime.configPath();
            Path current = configDir.resolve(configName);
            Path legacy = configDir.getParent().resolve(legacyName);
            File f = null;
            if (Files.isRegularFile(current)) f = current.toFile();
            else if (Files.isRegularFile(legacy)) f = legacy.toFile();
            else if ("baseline.json".equals(configName)) {
                File mcLegacy = new File("config/MICxToolkit_baseline.json");
                if (mcLegacy.exists()) f = mcLegacy;
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
