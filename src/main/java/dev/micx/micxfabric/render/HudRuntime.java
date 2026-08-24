package dev.micx.micxfabric.render;

import dev.micx.micxfabric.AimLeadModule;
import dev.micx.micxfabric.AsrModule;
import dev.micx.micxfabric.DpsCounterModule;
import dev.micx.micxfabric.LastMobsModule;
import dev.micx.micxfabric.TeammateHpModule;
import dev.micx.micxfabric.TeamSyncModule;
import dev.micx.micxfabric.ToroHealthModule;
import dev.micx.micxfabric.ToggleSprintModule;
import dev.micx.micxfabric.LrIndicatorModule;
import dev.micx.micxfabric.ZombiesAssistModule;
import net.minecraft.resources.Identifier;

public final class HudRuntime {
    private static boolean initialized;

    private HudRuntime() {
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        HudBackend backend = new VanillaHudBackend();
        backend.register(Identifier.fromNamespaceAndPath("micx-fabric", "asr"), AsrModule.instance()::drawHud);
        backend.register(Identifier.fromNamespaceAndPath("micx-fabric", "aim-lead"), AimLeadModule.instance()::drawHud);
        backend.register(Identifier.fromNamespaceAndPath("micx-fabric", "dps"), DpsCounterModule.instance()::drawHud);
        backend.register(Identifier.fromNamespaceAndPath("micx-fabric", "toro-health"), ToroHealthModule.instance()::drawHud);
        backend.register(Identifier.fromNamespaceAndPath("micx-fabric", "teammate-hp"), TeammateHpModule.instance()::drawHud);
        backend.register(Identifier.fromNamespaceAndPath("micx-fabric", "team-sync"), TeamSyncModule.instance()::drawHud);
        backend.register(Identifier.fromNamespaceAndPath("micx-fabric", "lr-indicator"), LrIndicatorModule.instance()::drawHud);
        backend.register(Identifier.fromNamespaceAndPath("micx-fabric", "last-mobs"), LastMobsModule.instance()::drawHud);
        backend.register(Identifier.fromNamespaceAndPath("micx-fabric", "zombies-assist"), ZombiesAssistModule.instance()::drawHud);
        backend.register(Identifier.fromNamespaceAndPath("micx-fabric", "toggle-sprint"), ToggleSprintModule.instance()::drawHud);
    }
}
