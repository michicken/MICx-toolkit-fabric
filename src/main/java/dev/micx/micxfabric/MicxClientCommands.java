package dev.micx.micxfabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Client-only /micx command tree. It never forwards these controls to a server. */
public final class MicxClientCommands {
    private static boolean registered;

    private MicxClientCommands() {
    }

    public static void initialize() {
        if (registered) return;
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("micx")
                        .executes(context -> openPanel(context.getSource()))
                        .then(ClientCommands.literal("panel")
                                .executes(context -> openPanel(context.getSource())))
                        .then(ClientCommands.literal("gui")
                                .executes(context -> openPanel(context.getSource())))
                        .then(ClientCommands.argument("args", StringArgumentType.greedyString())
                                .executes(context -> handle(context.getSource(),
                                        StringArgumentType.getString(context, "args"))))));
        registered = true;
    }

    private static int openPanel(FabricClientCommandSource source) {
        FabricRuntime.requestMainPanel(source.getClient());
        return 1;
    }

    private static int handle(FabricClientCommandSource source, String raw) {
        String[] args = raw == null || raw.isBlank() ? new String[0] : raw.trim().split("\\s+");
        if (args.length == 0) {
            return openPanel(source);
        }
        String top = args[0].toLowerCase(Locale.ROOT);
        switch (top) {
            case "list" -> printList(source);
            case "panel", "gui" -> openPanel(source);
            case "copy" -> copyToClipboard(source, args);
            case "toggle" -> toggle(source, args);
            case "pv", "playervisibility" -> playerVisibility(source, args);
            case "toro", "torohealth" -> toro(source, args);
            case "dps" -> dps(source, args);
            case "teammate", "thp" -> teammate(source, args);
            case "rc", "rightclicker" -> rightClicker(source, args);
            case "kbc", "kbclicker" -> keyboardClicker(source, args);
            case "sc", "skillcast" -> skillCast(source, args);
            case "teamsync", "ts" -> teamSync(source, args);
            case "sr", "speedrun" -> speedrun(source, args);
            case "lr" -> lr(source, args);
            case "hs" -> HsDispatchService.instance().dispatch(source, args);
            case "reset" -> reset(source);
            default -> printList(source);
        }
        return 1;
    }

    private static int copyToClipboard(FabricClientCommandSource source, String[] args) {
        if (args.length < 2) {
            reply(source, "§cusage: /micx copy <text>");
            return 0;
        }
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        source.getClient().keyboardHandler.setClipboard(text);
        reply(source, "§a已复制: §f" + text);
        return 1;
    }

    private static void printList(FabricClientCommandSource source) {
        reply(source, "Modules (" + ModulePanelRegistry.all().size() + "):");
        for (ModulePanelDescriptor descriptor : ModulePanelRegistry.all()) {
            String state = descriptor.isBlocked() ? "BLOCKED" : descriptor.isMigrated()
                    ? (descriptor.module().enabled() ? "LIVE" : "OFF") : "NOT MIGRATED";
            reply(source, "  " + state + " " + descriptor.displayName() + " (" + descriptor.id() + ")");
        }
    }

    private static void toggle(FabricClientCommandSource source, String[] args) {
        if (args.length < 2) {
            printList(source);
            return;
        }
        ModulePanelDescriptor descriptor = ModulePanelRegistry.get(args[1]);
        if (descriptor == null) {
            reply(source, "unknown module: " + args[1]);
            return;
        }
        if (!descriptor.isControllable()) {
            reply(source, descriptor.displayName() + " is "
                    + (descriptor.isBlocked() ? "blocked" : "not migrated"));
            return;
        }
        Module module = descriptor.module();
        ModuleRuntime.setEnabled(module.id(), !module.enabled());
        reply(source, (module.enabled() ? "Enabled " : "Disabled ") + descriptor.displayName());
    }

    private static void playerVisibility(FabricClientCommandSource source, String[] args) {
        PlayerVisibilityModule module = PlayerVisibilityModule.instance();
        if (!module.enabled()) {
            reply(source, "PlayerVisibility is OFF. /micx toggle player_visibility first");
            return;
        }
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "show";
        switch (sub) {
            case "show", "info" -> reply(source, "PV: active=" + (module.active() ? "ON" : "OFF")
                    + " mode=" + (module.hideMode() ? "hide" : "fade")
                    + " opacity=" + module.opacity() + " range=" + String.format(Locale.ROOT, "%.1f", module.range()));
            case "mode" -> {
                boolean fade = args.length >= 3 && (args[2].equalsIgnoreCase("fade") || args[2].equalsIgnoreCase("alpha"));
                module.setHideMode(!fade);
                reply(source, fade ? "PV mode: fade (alpha " + module.opacity() + ")" : "PV mode: hide (totally invisible)");
            }
            case "range" -> {
                if (args.length < 3) reply(source, "PV range: " + module.range() + " blocks");
                else try {
                    module.setRange(Float.parseFloat(args[2]));
                    reply(source, "PV range: " + module.range() + " blocks");
                } catch (NumberFormatException exception) {
                    reply(source, "usage: /micx pv range <blocks>");
                }
            }
            case "opacity", "alpha" -> {
                if (args.length < 3) reply(source, "PV opacity: " + module.opacity());
                else try {
                    module.setOpacity(Float.parseFloat(args[2]));
                    reply(source, "PV opacity: " + module.opacity());
                } catch (NumberFormatException exception) {
                    reply(source, "usage: /micx pv opacity <0.05-1.0>");
                }
            }
            default -> reply(source, "pv [show|mode|range|opacity]");
        }
    }

    private static void toro(FabricClientCommandSource source, String[] args) {
        ToroHealthModule module = ToroHealthModule.instance();
        if (!module.enabled()) {
            reply(source, "ToroHealth OFF. /micx toggle toro_health first");
            return;
        }
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "show";
        switch (sub) {
            case "show" -> reply(source, "Toro: mode=" + toroMode(module.displayMode())
                    + " part=" + module.showDamageParticles() + " target=crosshair_loaded_mob");
            case "mode" -> {
                if (args.length < 3) {
                    reply(source, "Toro mode: " + toroMode(module.displayMode()));
                    return;
                }
                String mode = args[2].toUpperCase(Locale.ROOT);
                int value = switch (mode) {
                    case "HEARTS" -> 0;
                    case "NUMERIC" -> 1;
                    case "OFF" -> 2;
                    default -> -1;
                };
                if (value < 0) reply(source, "toro mode: [HEARTS, NUMERIC, OFF]");
                else {
                    module.setDisplayMode(value);
                    reply(source, "Toro mode: " + mode);
                }
            }
            case "particles" -> {
                boolean value = args.length >= 3 ? Boolean.parseBoolean(args[2]) : !module.showDamageParticles();
                module.setShowDamageParticles(value);
                reply(source, "Toro particles: " + value);
            }
            case "hidedelay" -> {
                if (args.length < 3) reply(source, "Toro hide delay: " + module.hideDelayMs() + " ms");
                else try {
                    module.setHideDelayMs(Long.parseLong(args[2]));
                    reply(source, "Toro hide delay: " + module.hideDelayMs() + " ms");
                } catch (NumberFormatException exception) {
                    reply(source, "toro hidedelay <ms>");
                }
            }
            default -> reply(source, "toro [show|mode|particles|hidedelay]");
        }
    }

    private static String toroMode(int value) {
        return value == 1 ? "NUMERIC" : value == 2 ? "OFF" : "HEARTS";
    }

    private static void dps(FabricClientCommandSource source, String[] args) {
        DpsCounterModule module = DpsCounterModule.instance();
        if (!module.enabled()) {
            reply(source, "DPSCounter OFF. /micx toggle dps_counter first");
            return;
        }
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "show";
        if (sub.equals("show")) reply(source, "DPS: overlay=" + module.overlayEnabled() + " live=" + module.dps());
        else if (sub.equals("toggle")) {
            module.setOverlayEnabled(!module.overlayEnabled());
            reply(source, "DPS overlay: " + (module.overlayEnabled() ? "ON" : "OFF"));
        } else reply(source, "dps [show|toggle]");
    }

    private static void teammate(FabricClientCommandSource source, String[] args) {
        TeammateHpModule module = TeammateHpModule.instance();
        if (!module.enabled()) {
            reply(source, "TeammateHP OFF. /micx toggle teammate_hp first");
            return;
        }
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "show";
        switch (sub) {
            case "show" -> reply(source, "TeammateHP: overlay=" + module.active()
                    + " showHidden=" + module.showHidden() + " showDist=" + module.showDistance());
            case "toggle" -> {
                module.setActive(!module.active());
                reply(source, "TeammateHP: " + (module.active() ? "ON" : "OFF"));
            }
            case "hidden" -> {
                module.setShowHidden(!module.showHidden());
                reply(source, "TeammateHP showHidden: " + module.showHidden());
            }
            case "distance" -> {
                module.setShowDistance(!module.showDistance());
                reply(source, "TeammateHP showDistance: " + module.showDistance());
            }
            default -> reply(source, "teammate [show|toggle|hidden|distance]");
        }
    }

    private static void rightClicker(FabricClientCommandSource source, String[] args) {
        RightClickerModule module = RightClickerModule.instance();
        if (!module.enabled()) {
            reply(source, "RightClicker OFF. /micx toggle right_clicker first");
            return;
        }
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "show";
        if (sub.equals("show")) reply(source, "RightClicker: " + (module.active() ? "ON" : "OFF")
                + " CPS " + module.getMinCps() + "-" + module.getMaxCps());
        else if (sub.equals("toggle")) {
            module.setActive(!module.active());
            reply(source, "RightClicker: " + (module.active() ? "ON" : "OFF"));
        } else if (sub.equals("cps")) {
            if (args.length < 4) {
                reply(source, "usage: /micx rc cps <min> <max> (1-50)");
                return;
            }
            try {
                int min = Integer.parseInt(args[2]);
                int max = Integer.parseInt(args[3]);
                module.setCpsRange(min, max);
                reply(source, "RightClicker CPS " + module.getMinCps() + "-" + module.getMaxCps());
            } catch (NumberFormatException exception) {
                reply(source, "usage: /micx rc cps <min> <max> (1-50)");
            }
        } else reply(source, "rc [show|toggle|cps <min> <max>]");
    }

    private static void keyboardClicker(FabricClientCommandSource source, String[] args) {
        KeyboardClickerModule module = KeyboardClickerModule.instance();
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "show";
        if (sub.equals("show")) reply(source, "自动切枪 AutoSwitch: " + (module.modeIndex() == 0 ? "OFF" : "ON")
                + " mode=" + module.modeName());
        else if (sub.equals("toggle")) {
            if (!module.enabled()) {
                reply(source, "KeyboardClicker OFF. /micx toggle keyboard_clicker first");
                return;
            }
            module.onPrimaryPressed(source.getClient(), false);
            reply(source, "KeyboardClicker: " + (module.modeIndex() == 0 ? "OFF" : "ON")
                    + " mode=" + module.modeName());
        } else if (sub.equals("mode")) {
            module.onSecondaryPressed(source.getClient());
            reply(source, "KeyboardClicker mode=" + module.modeName());
        } else reply(source, "kbc [show|toggle|mode]");
    }

    private static void skillCast(FabricClientCommandSource source, String[] args) {
        SkillCastModule module = SkillCastModule.instance();
        reply(source, "SkillCast: key=" + new InputBinding(module.keyCode()).label()
                + " state=" + (module.enabled() ? "ready" : "disabled"));
    }

    private static void teamSync(FabricClientCommandSource source, String[] args) {
        TeamSyncModule module = TeamSyncModule.instance();
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "show";
        switch (sub) {
            case "show", "status" -> reply(source, "TeamSync: enabled=" + module.enabled()
                    + " connection=" + (module.connected() ? "connected" : "disconnected")
                    + " joined=" + module.joined() + " peers=" + module.state().all().size());
            case "toggle" -> {
                ModuleRuntime.setEnabled(module.id(), !module.enabled());
                reply(source, "TeamSync: " + (module.enabled() ? "ON" : "OFF"));
            }
            case "hud" -> {
                TeamSyncConfig config = module.config();
                if (args.length < 3) config.renderOverlay = !config.renderOverlay;
                else config.renderOverlay = Boolean.parseBoolean(args[2]);
                config.save();
                reply(source, "TeamSync HUD: " + (config.renderOverlay ? "ON" : "OFF"));
            }
            case "world", "markers" -> {
                TeamSyncConfig config = module.config();
                if (args.length < 3) config.renderWorld = !config.renderWorld;
                else config.renderWorld = Boolean.parseBoolean(args[2]);
                config.save();
                reply(source, "TeamSync world markers: " + (config.renderWorld ? "ON" : "OFF"));
            }
            case "target" -> {
                TeamSyncConfig config = module.config();
                if (args.length < 3) config.localAimFallback = !config.localAimFallback;
                else config.localAimFallback = Boolean.parseBoolean(args[2]);
                config.save();
                reply(source, "TeamSync local target: " + (config.localAimFallback ? "ON" : "OFF"));
            }
            default -> reply(source, "teamsync [show|toggle|hud|world|target] [on|off]");
        }
    }

    private static void lr(FabricClientCommandSource source, String[] args) {
        LrIndicatorModule m = LrIndicatorModule.instance();
        if (args.length >= 2) {
            try { int pos = Integer.parseInt(args[1]); m.setRotationPosition(pos); reply(source, "LR rotation: " + m.lrRotationPosition() + (pos==1?" (off)":"")); return; } catch (NumberFormatException ignored) {}
        }
        reply(source, "LR rotation: " + m.lrRotationPosition() + " beep=" + m.lrBeepEnabled() + " usage: /micx lr <1-4> (1=off)");
    }

    private static void speedrun(FabricClientCommandSource source, String[] args) {
        ZombiesConfig cfg = ZombiesAssistModule.instance().config();
        if (args.length >= 2 && "off".equalsIgnoreCase(args[1])) cfg.speedrunEnabled = false;
        else if (args.length >= 2 && "on".equalsIgnoreCase(args[1])) cfg.speedrunEnabled = true;
        else cfg.speedrunEnabled = !cfg.speedrunEnabled;
        ZombiesAssistModule.instance().saveConfig();
        SpeedrunBaseline bl = SpeedrunBaseline.get();
        String state = cfg.speedrunEnabled ? "ON" : "OFF";
        String base;
        if (bl.hasBaseline() && bl.hasBaselineB()) base = " baselines R"+bl.rounds()+" ["+bl.labelA()+"+"+bl.labelB()+"]";
        else if (bl.hasBaseline()) base = " baseline R"+bl.rounds()+" "+(bl.source()==null?"":bl.source());
        else base = " no baseline";
        reply(source, "Speedrun: "+state+base);
    }

    private static void reset(FabricClientCommandSource source) {
        for (Module module : ModuleRuntime.modules()) {
            boolean desired = module.defaultEnabled();
            if (module.enabled() != desired) ModuleRuntime.setEnabled(module.id(), desired);
            module.resetInput();
        }
        reply(source, "MICx module states reset to defaults.");
        printList(source);
    }

    private static void reply(FabricClientCommandSource source, String message) {
        source.sendFeedback(ChatMessageStyles.feedback(message));
    }
}
