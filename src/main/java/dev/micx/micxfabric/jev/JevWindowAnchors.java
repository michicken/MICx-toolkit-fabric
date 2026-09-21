package dev.micx.micxfabric.jev;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.player.LocalPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Explicit, map-specific walkable anchors for Alien Arcadium windows.
 *
 * <p>The raw window marker can sit beyond the three grey carpets, so collision probing alone
 * cannot prove that Baritone can reach it.  An anchor is therefore learned only after a human
 * stands at the correct window edge and calls {@code calibrate_window}.  Until then the window
 * deliberately has no movement target.
 */
public final class JevWindowAnchors {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("MICxToolkit").resolve("jev-aa-window-anchors.json");
    private static JsonObject anchors;

    private JevWindowAnchors() {
    }

    public static synchronized JsonObject edgeFor(String windowId) {
        load();
        if (!anchors.has(windowId) || !anchors.get(windowId).isJsonObject()) return null;
        JsonObject saved = anchors.getAsJsonObject(windowId);
        if (!saved.has("x") || !saved.has("y") || !saved.has("z")) return null;
        JsonObject out = saved.deepCopy();
        out.addProperty("range", saved.has("range") ? saved.get("range").getAsInt() : 2);
        out.addProperty("source", "manual_calibration");
        return out;
    }

    public static synchronized String calibrate(String windowId, LocalPlayer player) {
        if (windowId == null || windowId.isBlank()) return "error: missing window id";
        if (player == null) return "error: no player";
        load();
        JsonObject edge = new JsonObject();
        edge.addProperty("x", round(player.getX()));
        edge.addProperty("y", round(player.getY()));
        edge.addProperty("z", round(player.getZ()));
        edge.addProperty("range", 2);
        anchors.add(windowId, edge);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, anchors.toString(), StandardCharsets.UTF_8);
            return "ok";
        } catch (IOException e) {
            return "error: saving window anchor: " + e;
        }
    }

    private static void load() {
        if (anchors != null) return;
        anchors = new JsonObject();
        try {
            if (!Files.exists(FILE)) return;
            var parsed = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8));
            if (parsed.isJsonObject()) anchors = parsed.getAsJsonObject();
        } catch (Exception ignored) {
            // A malformed calibration file must not make the state bridge unavailable.
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
