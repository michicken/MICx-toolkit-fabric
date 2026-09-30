package dev.micx.micxfabric;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 新手友好分支：首次启动时把内置的调优配置写进 config/MICxToolkit/。
 * 只复制缺失的文件，绝不覆盖用户已有配置；API key 留空由用户自填。
 */
public final class StarterDefaults {
    private static final String RESOURCE_DIR = "/assets/micx-fabric/defaults/";
    private static final List<String> FILES = List.of(
            "aim-lead.properties",
            "aimbot.properties",
            "anti-reshift.properties",
            "asr.properties",
            "auto-text.properties",
            "chams.properties",
            "chat-translate.properties",
            "dps-counter.properties",
            "eco-rate.properties",
            "esp.properties",
            "hud-layout.properties",
            "keyboard-clicker.properties",
            "lr-indicator.properties",
            "magnet.properties",
            "player-outline-esp.properties",
            "player-visibility.properties",
            "revive-aura.properties",
            "right-clicker.properties",
            "slime-forecast.properties",
            "teammate-hp.properties",
            "teamsync.properties",
            "toro-health.properties",
            "view-hold.properties",
            "window-spawn-counter.properties",
            "zombie-fade.properties",
            "zombies-assist.properties",
            "zoom-scope.properties",
            "micx-panel.properties",
            "module-states.properties"
    );

    private StarterDefaults() {
    }

    public static void install(Path configDir) {
        int written = 0;
        try {
            Files.createDirectories(configDir);
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("StarterDefaults: unable to create {}", configDir, exception);
            return;
        }
        for (String name : FILES) {
            Path target = configDir.resolve(name);
            if (Files.isRegularFile(target)) continue;
            try (InputStream input = StarterDefaults.class.getResourceAsStream(RESOURCE_DIR + name)) {
                if (input == null) {
                    MicxFabric.LOGGER.warn("StarterDefaults: bundled default missing: {}", name);
                    continue;
                }
                Files.copy(input, target);
                written++;
            } catch (IOException exception) {
                MicxFabric.LOGGER.warn("StarterDefaults: unable to write {}", name, exception);
            }
        }
        if (written > 0) {
            MicxFabric.LOGGER.info("StarterDefaults: wrote {} starter config file(s) into {}", written, configDir);
        }
    }
}
