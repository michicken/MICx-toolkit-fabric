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

public final class SpeedrunBaseline {
    private static final SpeedrunBaseline INSTANCE = new SpeedrunBaseline();
    public static SpeedrunBaseline get() { return INSTANCE; }

    private volatile long[] perRoundMs = null;
    private volatile long[] cumulativeMs = null;
    private volatile long totalMs = 0;
    private volatile int rounds = 0;
    private volatile boolean loaded = false;
    private volatile String source = null;

    private SpeedrunBaseline() {}

    public synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            JsonObject obj = null;
            Path current = FabricRuntime.configPath().resolve("baseline.json");
            Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_baseline.json");
            File f = null;
            if (Files.isRegularFile(current)) f = current.toFile();
            else if (Files.isRegularFile(legacy)) f = legacy.toFile();
            else {
                File mcLegacy = new File("config/MICxToolkit_baseline.json");
                if (mcLegacy.exists()) f = mcLegacy;
            }
            if (f != null && f.exists()) {
                obj = JsonParser.parseReader(new FileReader(f, StandardCharsets.UTF_8)).getAsJsonObject();
            } else {
                try (InputStream is = getClass().getResourceAsStream("/baseline/aa105.json")) {
                    if (is != null) {
                        obj = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
                    }
                }
            }
            if (obj == null) return;
            JsonArray per = obj.getAsJsonArray("perRoundMs");
            JsonArray cum = obj.getAsJsonArray("cumulativeMs");
            if (per == null || cum == null) return;
            int n = per.size();
            long[] p = new long[n];
            long[] c = new long[n];
            for (int i = 0; i < n; i++) p[i] = per.get(i).getAsLong();
            for (int i = 0; i < n; i++) c[i] = cum.get(i).getAsLong();
            perRoundMs = p;
            cumulativeMs = c;
            rounds = n;
            if (obj.has("totalMs")) totalMs = obj.get("totalMs").getAsLong();
            else if (n > 0) totalMs = c[n - 1];
            if (obj.has("source")) source = obj.get("source").getAsString();
        } catch (Throwable ignored) {
            perRoundMs = null;
            cumulativeMs = null;
        }
    }

    public boolean hasBaseline() { load(); return perRoundMs != null && rounds > 0; }
    public long baselineMs(int round) { load(); if (perRoundMs == null || round < 1 || round > rounds) return -1; return perRoundMs[round - 1]; }
    public long cumulativeBaselineMs(int round) { load(); if (cumulativeMs == null || round < 1 || round > rounds) return -1; return cumulativeMs[round - 1]; }
    public int rounds() { load(); return rounds; }
    public String source() { load(); return source; }

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
