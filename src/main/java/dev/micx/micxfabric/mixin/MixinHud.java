package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.ZombiesAssistModule;
import dev.micx.micxfabric.ZombiesOverlayDecision;
import dev.micx.micxfabric.ZombiesTracker;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses vanilla BossBar/sidebar only while the Zombies replacement HUD owns them. */
@Mixin(Hud.class)
public abstract class MixinHud {
    @Inject(
            method = "extractBossOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void micx$hideBossBar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
                                  CallbackInfo callbackInfo) {
        ZombiesAssistModule module = ZombiesAssistModule.instance();
        if (ZombiesOverlayDecision.hideBossBar(module.enabled(), module.overlayEnabled(),
                ZombiesTracker.instance().isInZombies())) {
            callbackInfo.cancel();
        }
    }

    @Inject(
            method = "extractScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void micx$hideSidebar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
                                  CallbackInfo callbackInfo) {
        ZombiesAssistModule module = ZombiesAssistModule.instance();
        if (ZombiesOverlayDecision.hideSidebar(module.enabled(), module.overlayEnabled(),
                ZombiesTracker.instance().isInZombies(), module.config().originalScoreboard,
                module.config().showEconomy)) {
            callbackInfo.cancel();
        }
    }
}
