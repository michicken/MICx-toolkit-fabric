package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ModuleRuntime {
    private static final Map<String, Module> MODULES = new LinkedHashMap<>();
    private static boolean initialized;

    private ModuleRuntime() {
    }

    public static void initialize() {
        if (initialized) return;
        register(SwordBlockModule.instance());
        register(ChatCleanerModule.instance());
        register(ChatCopyModule.instance());
        register(ChatTranslateModule.instance());
        register(AsrModule.instance());
        register(SkillCastModule.instance());
        register(KeyboardClickerModule.instance());
        register(PlayerVisibilityModule.instance());
        register(DpsCounterModule.instance());
        register(ToroHealthModule.instance());
        register(TeammateHpModule.instance());
        register(ZombiesAssistModule.instance());
        register(LrIndicatorModule.instance());
        register(FullbrightModule.instance());
        register(EspModule.instance());
        register(ChamsModule.instance());
        register(PlayerOutlineEspModule.instance());
        register(AimLeadModule.instance());
        register(TeamSyncModule.instance());
        register(ToggleSprintModule.instance());
        register(WelcomeModule.instance());
        register(AutoTextModule.instance());
        register(AutoHideVisualsModule.instance());
        register(ZombieFadeModule.instance());
        register(ReviveAuraModule.instance());
        register(LastMobsModule.instance());
        register(SpawnMarkerModule.instance());
        register(SlimeForecastModule.instance());
        register(GolemMarkerModule.instance());
        register(ViewHoldModule.instance());
        register(MagnetModule.instance());
        register(AntiReshiftModule.instance());
        register(AntiAxeModule.instance());
        register(RoundTimerModule.instance());
        register(EcoRateModule.instance());
        register(RightClickerModule.instance());
        for (Module module : MODULES.values()) {
            module.setEnabled(ModuleStateStore.get(module.id(), module.defaultEnabled()));
        }
        initialized = true;
    }

    public static void register(Module module) {
        if (module == null) throw new IllegalArgumentException("module");
        if (MODULES.putIfAbsent(module.id(), module) != null) {
            throw new IllegalStateException("Duplicate module id: " + module.id());
        }
    }

    public static Module get(String id) {
        return MODULES.get(id);
    }

    public static List<Module> modules() {
        return Collections.unmodifiableList(MODULES.values().stream().toList());
    }

    public static void setEnabled(String id, boolean enabled) {
        Module module = get(id);
        if (module == null) throw new IllegalArgumentException("Unknown module: " + id);
        module.setEnabled(enabled);
        if (!enabled) {
            module.resetInput();
            HotkeyRuntime.clearModule(id);
            HotkeyRuntimeReleased.clearModule(id);
        }
    }

    public static void tick(Minecraft client) {
        HotkeyRuntime.tick(client);
        for (Module module : MODULES.values()) {
            if (module.enabled()) module.tick(client);
        }
    }

    /** Clears transient state for every registered module on disconnect/stop. */
    public static void resetTransientState() {
        for (Module module : MODULES.values()) {
            try {
                module.resetState();
            } catch (RuntimeException exception) {
                MicxFabric.LOGGER.warn("Unable to reset module state: {}", module.id(), exception);
            }
        }
    }
}
