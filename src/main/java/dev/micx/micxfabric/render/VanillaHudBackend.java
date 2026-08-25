package dev.micx.micxfabric.render;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class VanillaHudBackend implements HudBackend {

    private static final Set<Identifier> LOGGED = ConcurrentHashMap.newKeySet();

    @Override
    public void register(Identifier id, HudDrawer drawer) {
        // 单抽屉异常隔离：一个模块 drawHud 抛错不能拖垮整帧其他 HUD；每 id 只记一次日志
        HudElementRegistry.addLast(id, (graphics, deltaTracker) -> {
            try {
                drawer.draw(graphics);
            } catch (RuntimeException ex) {
                if (LOGGED.add(id)) {
                    dev.micx.micxfabric.MicxFabric.LOGGER.error("[MICx] HUD drawer {} threw; suppressing further reports", id, ex);
                }
            }
        });
    }
}
