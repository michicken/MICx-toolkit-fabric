package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ModuleRuntime {
    private static final Map<String, Module> MODULES = new LinkedHashMap<>();
    /** 互斥对（keyA→keyB 双向）：开启时自动关闭对方（后开者胜），恢复时先注册者胜。 */
    private static final Map<String, String> MUTEX = new HashMap<>();
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
        register(AimbotModule.instance());
        register(AimbotHudModule.instance());
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
        register(LegacySneakVisualsModule.instance());
        register(ParticleFreeModule.instance());
        register(RoundTimerModule.instance());
        register(EcoRateModule.instance());
        register(RightClickerModule.instance());
        register(NoReloadModule.instance());
        register(ZombiesExplorerModule.instance());
        register(WindowSpawnCounterModule.instance());
        register(WaveSpawnSoundModule.instance());
        register(ZoomScopeModule.instance());
        registerMutex("noreload", "keyboard_clicker");
        for (Module module : MODULES.values()) {
            boolean want = ModuleStateStore.get(module.id(), module.defaultEnabled());
            if (want) {
                // 互斥恢复冲突：先注册者胜（对方已启用则跳过自己，保持对方）
                Module partner = mutexPartner(module.id());
                if (partner != null && partner.enabled()) continue;
            }
            module.setEnabled(want);
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

    /** 声明两个模块互斥：任一开启时自动关闭另一个（运行时后开者胜）。 */
    public static void registerMutex(String keyA, String keyB) {
        MUTEX.put(keyA, keyB);
        MUTEX.put(keyB, keyA);
    }

    private static Module mutexPartner(String id) {
        String other = MUTEX.get(id);
        return other == null ? null : MODULES.get(other);
    }

    public static List<Module> modules() {
        return Collections.unmodifiableList(MODULES.values().stream().toList());
    }

    public static void setEnabled(String id, boolean enabled) {
        Module module = get(id);
        if (module == null) throw new IllegalArgumentException("Unknown module: " + id);
        if (enabled && !module.enabled()) {
            // 互斥：开启前先关闭对方（后开者胜）
            Module partner = mutexPartner(id);
            if (partner != null && partner.enabled()) {
                partner.setEnabled(false);
                ModuleStateStore.put(partner.id(), false);
            }
        }
        module.setEnabled(enabled);
        if (!enabled) {
            module.resetInput();
            HotkeyRuntime.clearModule(id);
            HotkeyRuntimeReleased.clearModule(id);
        }
    }

    // [micx-tickprof] 诊断已移除

    public static void tick(Minecraft client) {
        HotkeyRuntime.tick(client);
        for (Module module : MODULES.values()) {
            if (!module.enabled()) continue;
            module.tick(client);
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
