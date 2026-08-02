package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.RightClickerModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps vanilla's native right-click path while removing only its client cooldown. */
@Mixin(Minecraft.class)
public final class MixinMinecraftRightClicker {
    @Inject(method = "tick", at = @At("HEAD"))
    private void micx$clearRightClickDelay(CallbackInfo callbackInfo) {
        if (!RightClickerModule.isActive()) return;
        Minecraft client = (Minecraft) (Object) this;
        if (client.player == null || client.level == null || client.gui.screen() != null
                || !client.options.keyUse.isDown() || client.player.isUsingItem()) return;
        // 26.2 exposes the counter privately; the mixin field accessor below writes it safely.
        ((RightClickDelayAccess) (Object) client).micx$setRightClickDelay(0);
    }
}
