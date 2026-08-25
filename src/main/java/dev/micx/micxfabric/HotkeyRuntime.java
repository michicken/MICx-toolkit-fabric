package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/** Polls migrated module bindings with Forge-compatible press-edge semantics. */
public final class HotkeyRuntime {
    private static final Map<String, Boolean> PREVIOUS = new HashMap<>();

    private HotkeyRuntime() {
    }

    public static void tick(Minecraft client) {
        boolean blocked = client == null
                || client.player == null
                || client.level == null
                || client.gui == null
                || client.gui.screen() != null;
        for (Module module : ModuleRuntime.modules()) {
            int[] chord = module.primaryChord();
            if (chord != null) {
                handleChord(module, module.id() + ":primary", chord, blocked, client);
            } else {
                handleBinding(module, module.id() + ":primary", module.primaryBinding(), blocked, client, true);
            }
            handleBinding(module, module.id() + ":secondary", module.secondaryBinding(), blocked, client, false);
            // 面板侧统一绑定：模块无自有快捷键字段时，组合键直接当模块总开关
            int[] panelChord = ModulePanelRegistry.panelChord(module.id());
            if (chord == null && module.primaryBinding() == null
                    && panelChord != null && !KeyChord.isEmpty(panelChord)) {
                handlePanelToggle(module, module.id() + ":panel", panelChord, blocked, client);
            }
        }
    }

    /** 面板侧绑定：all-down 上升沿切换模块开关并聊天提示。 */
    private static void handlePanelToggle(Module module, String stateId, int[] chord,
                                          boolean blocked, Minecraft client) {
        boolean down = KeyChord.isAllDown(chord, client);
        Boolean old = PREVIOUS.get(stateId);
        PREVIOUS.put(stateId, down);
        if (blocked || !down || Boolean.TRUE.equals(old)) return;
        boolean next = !module.enabled();
        module.setEnabled(next);
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(ChatMessageStyles.notice(
                    module.id() + " " + (next ? "ON" : "OFF")));
        }
    }

    /** 组合键 all-down 上升沿：与单键共用边沿状态与回调。 */
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
        boolean newlyEnabled = !module.enabled();
        if (newlyEnabled) module.setEnabled(true);
        if (!module.enabled()) return;
        module.onPrimaryPressed(client, newlyEnabled);
    }

    private static void handleBinding(Module module, String stateId, InputBinding binding,
                                      boolean blocked, Minecraft client, boolean primary) {
        if (binding == null || binding.isUnbound()) {
            PREVIOUS.remove(stateId);
            return;
        }
        boolean down = binding.down(client);
        boolean old = PREVIOUS.getOrDefault(stateId, false);
        PREVIOUS.put(stateId, down);
        if (blocked || !down || old) return;
        boolean newlyEnabled = !module.enabled();
        if (newlyEnabled) module.setEnabled(true);
        if (!module.enabled()) return;
        if (primary) module.onPrimaryPressed(client, newlyEnabled);
        else module.onSecondaryPressed(client);
    }

    /** Clears both primary and secondary edge state for one module. */
    public static void clearModule(String moduleId) {
        if (moduleId == null) return;
        PREVIOUS.remove(moduleId);
        PREVIOUS.remove(moduleId + ":primary");
        PREVIOUS.remove(moduleId + ":secondary");
        PREVIOUS.remove(moduleId + ":panel");
    }

    /** Backward-compatible alias used by older callers. */
    public static void clear(String id) {
        clearModule(id);
    }

    public static void clearAll() {
        PREVIOUS.clear();
    }
}
