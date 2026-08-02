package dev.micx.micxfabric;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import dev.micx.micxfabric.render.HudRuntime;
import dev.micx.micxfabric.render.RenderCapabilities;
import dev.micx.micxfabric.render.RenderCapabilityProbe;

import java.nio.file.Path;
import java.util.function.Consumer;

public final class FabricRuntime {
    private static boolean initialized;
    private static boolean mainPanelRequested;
    private static Consumer<Minecraft> mainPanelOpener = client -> {
    };

    private FabricRuntime() {
    }

    public static void initialize() {
        if (initialized) return;
        ModuleStateStore.initialize(configPath());
        MicxClientCommands.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ModuleRuntime.tick(client);
            if (mainPanelRequested) {
                mainPanelRequested = false;
                mainPanelOpener.accept(client);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((connection, client) -> resetTransientState());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> stopClient(client));
        ModuleRuntime.initialize();
        ZombiesEventBridge.initialize();
        ChatTranslateModule.instance().initializeConfig();
        AsrModule.instance().initializeConfig();
        setMainPanelOpener(client -> {
            if (client != null) client.setScreenAndShow(new MicxPanelScreen(null));
        });
        HudRuntime.initialize();
        RenderCapabilities capabilities = RenderCapabilityProbe.probe();
        MicxFabric.LOGGER.info("MICx modules ready; active HUD path=VANILLA_FABRIC, metalApiClasses={}, vulkanApiClasses={}",
                capabilities.hasMetalApiClasses(), capabilities.hasVulkanApiClasses());
        initialized = true;
    }

    private static void resetTransientState() {
        mainPanelRequested = false;
        HotkeyRuntime.clearAll();
        HotkeyRuntimeReleased.clear();
        if (ModuleRuntime.get("zombies_assist") != null) {
            ModuleRuntime.resetTransientState();
        }
        ZombiesEventBridge.reset();
    }

    private static void stopClient(Minecraft client) {
        resetTransientState();
        ChatTranslateModule.instance().shutdown();
        TeamSyncModule.instance().shutdown();
    }

    public static void requestMainPanel(Minecraft client) {
        if (client == null) return;
        mainPanelRequested = true;
    }

    public static void setMainPanelOpener(Consumer<Minecraft> opener) {
        mainPanelOpener = opener == null ? client -> {
        } : opener;
    }

    static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("MICxToolkit");
    }
}
