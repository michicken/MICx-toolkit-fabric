package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Polls migrated module bindings with Forge-compatible press-edge semantics. */
public final class HotkeyRuntime {
    private static final Map<String, Boolean> PREVIOUS = new HashMap<>();
    /** 组合键成员单键的挂起状态（2026-09-22 定稿：按下挂起、松开触发、组合键按齐作废）。 */
    private static final Map<String, HeldSingle> HELD = new HashMap<>();

    private HotkeyRuntime() {
    }

    private record HeldSingle(int code, ChordGuard.Phase phase) {
    }

    public static void tick(Minecraft client) {
        boolean blocked = client == null
                || client.player == null
                || client.level == null
                || client.gui == null
                || client.gui.screen() != null;
        // 第一遍：收集本 tick 生效的全部组合键（模块自带主组合键 + 面板侧组合键），
        // 供「组合键成员的单键让路」判定（ChordGuard）。
        List<int[]> chords = new ArrayList<>();
        for (Module module : ModuleRuntime.modules()) {
            int[] chord = module.primaryChord();
            if (chord != null && !KeyChord.isEmpty(chord)) {
                chords.add(chord);
                continue;
            }
            int[] panelChord = ModulePanelRegistry.panelChord(module.id());
            if (panelChord != null && !KeyChord.isEmpty(panelChord)) chords.add(panelChord);
        }
        for (Module module : ModuleRuntime.modules()) {
            int[] chord = module.primaryChord();
            int[] panelChord = ModulePanelRegistry.panelChord(module.id());
            boolean panelOverride = panelChord != null && !KeyChord.isEmpty(panelChord);
            if (chord != null) {
                handleChord(module, module.id() + ":primary", chord, blocked, client);
            } else if (panelOverride && module.primaryBinding() != null) {
                // 单键模块被面板组合键覆盖：仍走主键回调，语义与原单键一致（2026-09-22）。
                handleChord(module, module.id() + ":primary", panelChord, blocked, client);
            } else if (panelOverride) {
                // 无自带绑定的模块：组合键直接当模块总开关
                handlePanelToggle(module, module.id() + ":panel", panelChord, blocked, client);
            } else {
                handleBinding(module, module.id() + ":primary", module.primaryBinding(), blocked, client, true, chords);
            }
            handleBinding(module, module.id() + ":secondary", module.secondaryBinding(), blocked, client, false, chords);
        }
    }

    /** 面板侧绑定：all-down 上升沿切换模块开关并聊天提示。 */
    private static void handlePanelToggle(Module module, String stateId, int[] chord,
                                          boolean blocked, Minecraft client) {
        boolean down = KeyChord.isAllDown(chord, client);
        Boolean old = PREVIOUS.get(stateId);
        PREVIOUS.put(stateId, down);
        if (blocked || !down || Boolean.TRUE.equals(old)) return;
        suppressChordMembers(chord);
        boolean next = !module.enabled();
        module.setEnabled(next);
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(ChatMessageStyles.notice(
                    module.id() + " " + (next ? "ON" : "OFF")));
        }
    }

    /** 组合键 all-down 上升沿：上升沿把该组合键成员的挂起单键作废，再触发回调。 */
    private static void handleChord(Module module, String stateId, int[] chord,
                                    boolean blocked, Minecraft client) {
        if (KeyChord.isEmpty(chord)) {
            PREVIOUS.remove(stateId);
            return;
        }
        boolean down = KeyChord.isAllDown(chord, client);
        boolean old = PREVIOUS.getOrDefault(stateId, false);
        PREVIOUS.put(stateId, down);
        if (blocked || !down || old) return;
        suppressChordMembers(chord);
        boolean newlyEnabled = !module.enabled();
        if (newlyEnabled) module.setEnabled(true);
        if (!module.enabled()) return;
        module.onPrimaryPressed(client, newlyEnabled);
    }

    private static void handleBinding(Module module, String stateId, InputBinding binding,
                                      boolean blocked, Minecraft client, boolean primary,
                                      List<int[]> chords) {
        if (binding == null || binding.isUnbound()) {
            PREVIOUS.remove(stateId);
            HELD.remove(stateId);
            return;
        }
        boolean down = binding.down(client);
        boolean old = PREVIOUS.getOrDefault(stateId, false);
        PREVIOUS.put(stateId, down);
        ChordGuard.Phase phase = HELD.containsKey(stateId) ? HELD.get(stateId).phase() : null;
        ChordGuard.Step step = ChordGuard.advanceSingle(
                ChordGuard.isMember(binding.code(), chords), down, blocked, old, phase);
        switch (step) {
            case FIRE_NOW -> {
                HELD.remove(stateId);
                fireBinding(module, client, primary);
            }
            case ARM_PENDING -> HELD.put(stateId, new HeldSingle(binding.code(), ChordGuard.Phase.PENDING));
            case FIRE_ON_RELEASE -> {
                HELD.remove(stateId);
                fireBinding(module, client, primary);
            }
            case NONE -> {
                // 松开（无挂起/已作废）或按住进入 GUI：清挂起状态
                if (!down || blocked) HELD.remove(stateId);
            }
        }
    }

    private static void fireBinding(Module module, Minecraft client, boolean primary) {
        boolean newlyEnabled = !module.enabled();
        if (newlyEnabled) module.setEnabled(true);
        if (!module.enabled()) return;
        if (primary) module.onPrimaryPressed(client, newlyEnabled);
        else module.onSecondaryPressed(client);
    }

    /** 组合键按齐：把该组合键成员的挂起单键作废（这次按住期间松开不再触发）。 */
    private static void suppressChordMembers(int[] chord) {
        for (Map.Entry<String, HeldSingle> entry : HELD.entrySet()) {
            if (entry.getValue().phase() != ChordGuard.Phase.PENDING) continue;
            for (int k : chord) {
                if (k == 0) break;
                if (k == entry.getValue().code()) {
                    HELD.put(entry.getKey(), new HeldSingle(k, ChordGuard.Phase.SUPPRESSED));
                    break;
                }
            }
        }
    }

    /** Clears both primary and secondary edge state for one module. */
    public static void clearModule(String moduleId) {
        if (moduleId == null) return;
        PREVIOUS.keySet().removeIf(key -> key.equals(moduleId) || key.startsWith(moduleId + ":"));
        HELD.keySet().removeIf(key -> key.equals(moduleId) || key.startsWith(moduleId + ":"));
    }

    /** Backward-compatible alias used by older callers. */
    public static void clear(String id) {
        clearModule(id);
    }

    public static void clearAll() {
        PREVIOUS.clear();
        HELD.clear();
    }
}
