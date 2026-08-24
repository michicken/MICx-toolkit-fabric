package dev.micx.micxfabric;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class AutoHideVisualsModule implements Module {
    private static final AutoHideVisualsModule INSTANCE = new AutoHideVisualsModule();
    private static final String[] MANAGED_KEYS = {"esp", "chams", "player_outline_esp", "aim_lead"};
    private static final String STATE_FILE = "MICxToolkit_AutoHide.state";

    private boolean enabled;
    private boolean armed;
    private long hideAtMs;
    private long menuNullSinceMs;
    private final List<String> hiddenKeys = new ArrayList<>();
    private boolean registered;

    private AutoHideVisualsModule() {}

    public static AutoHideVisualsModule instance() { return INSTANCE; }

    @Override public String id() { return "auto_hide_visuals"; }
    @Override public boolean defaultEnabled() { return true; }
    @Override public boolean enabled() { return enabled; }
    @Override public void setEnabled(boolean v) {
        if (v == enabled) return;
        enabled = v;
        ModuleStateStore.put(id(), v);
        if (v) {
            restoreFromState();
            ensureRegistered();
        } else {
            if (armed) restoreVisuals();
        }
    }

    @Override public void tick(Minecraft client) {
        if (!enabled || !armed) return;
        long now = System.currentTimeMillis();
        ZombiesTracker tracker = ZombiesTracker.instance();
        if (AutoHideRules.restoreAllowed(hideAtMs, now, tracker.round(), tracker.isInZombies())) {
            restoreVisuals();
            return;
        }
        if (client != null && client.level == null) {
            if (menuNullSinceMs == 0L) menuNullSinceMs = now;
            if (now - menuNullSinceMs >= AutoHideRules.MENU_NULL_RESTORE_MS) restoreVisuals();
        } else {
            menuNullSinceMs = 0L;
        }
    }

    @Override public void resetState() {}

    private void ensureRegistered() {
        if (registered) return;
        registered = true;
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay || !enabled || armed || message == null) return;
            String text = message.getString();
            if (text == null) return;
            String stripped = strip(text);
            if (!AutoHideRules.isGameEndText(stripped)) return;
            if (!AutoHideRules.canTrigger(ZombiesTracker.instance().round())) return;
            hideVisuals();
        });
    }

    private void hideVisuals() {
        armed = true;
        hideAtMs = System.currentTimeMillis();
        hiddenKeys.clear();
        for (String key : MANAGED_KEYS) {
            Module m = ModuleRuntime.get(key);
            if (m != null && m.enabled()) {
                hiddenKeys.add(key);
                ModuleRuntime.setEnabled(key, false);
            }
        }
        saveState();
    }

    private void restoreVisuals() {
        armed = false;
        hideAtMs = 0L;
        menuNullSinceMs = 0L;
        List<String> toRestore = new ArrayList<>(hiddenKeys);
        hiddenKeys.clear();
        for (String key : toRestore) {
            Module m = ModuleRuntime.get(key);
            if (m != null && !m.enabled()) ModuleRuntime.setEnabled(key, true);
        }
        clearState();
    }

    private void restoreFromState() {
        Path f = stateFile();
        if (f == null || !Files.isRegularFile(f)) return;
        try {
            Properties p = new Properties();
            try (var in = Files.newInputStream(f)) { p.load(in); }
            if (!"true".equals(p.getProperty("armed"))) { clearState(); return; }
            String keys = p.getProperty("keys", "");
            for (String key : keys.split(",")) {
                if (key.isEmpty()) continue;
                Module m = ModuleRuntime.get(key);
                if (m != null && !m.enabled()) ModuleRuntime.setEnabled(key, true);
            }
            clearState();
        } catch (IOException ignored) {}
    }

    private void saveState() {
        Path f = stateFile();
        if (f == null) return;
        try {
            Files.createDirectories(f.getParent());
            Properties p = new Properties();
            p.setProperty("armed", "true");
            p.setProperty("hideAtMs", String.valueOf(hideAtMs));
            p.setProperty("keys", String.join(",", hiddenKeys));
            AtomicProperties.store(f, p, "MICx AutoHide state");
        } catch (IOException ignored) {}
    }

    private void clearState() {
        Path f = stateFile();
        if (f == null) return;
        try { Files.deleteIfExists(f); } catch (IOException ignored) {}
    }

    private static Path stateFile() {
        try { return FabricRuntime.configPath().resolve(STATE_FILE); } catch (Throwable t) { return null; }
    }

    private static String strip(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) { i++; continue; }
            sb.append(c);
        }
        return sb.toString();
    }
}
