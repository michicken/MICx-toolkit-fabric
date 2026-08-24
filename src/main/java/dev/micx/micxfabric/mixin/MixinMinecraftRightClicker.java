package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.RightClickerModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RightClicker single-engine guard: while firing, hold rightClickDelay at 4
 * to swallow vanilla's 5 CPS held-use chassis. Real clicks are injected
 * by RightClickerModule.tick via KeyMapping.click (once per tick).
 */
@Mixin(Minecraft.class)
public final class MixinMinecraftRightClicker {
    @Inject(method = "tick", at = @At("HEAD"))
    private void micx$suppressRightClickDelay(CallbackInfo callbackInfo) {
        if (!RightClickerModule.isActive()) return;
        Minecraft client = (Minecraft) (Object) this;
        if (client.player == null || client.level == null || client.gui.screen() != null
                || !client.options.keyUse.isDown() || client.player.isUsingItem()) return;
        if (!RightClickerModule.instance().isSupplyingFire()) return;
        ((RightClickDelayAccess) (Object) client).micx$setRightClickDelay(4);
    }
}
