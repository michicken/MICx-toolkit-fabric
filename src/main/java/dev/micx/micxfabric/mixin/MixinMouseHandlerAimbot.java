package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.AimbotModule;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the Aimbot mouse policy before vanilla consumes per-frame deltas. */
@Mixin(MouseHandler.class)
public abstract class MixinMouseHandlerAimbot {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void micx$aimbotTurn(double time, CallbackInfo callbackInfo) {
        int action = AimbotModule.instance().onMouseTurn(accumulatedDX, accumulatedDY);
        if (action == AimbotModule.MOUSE_LOCK_PITCH) {
            // Let vanilla apply horizontal input, but keep manual pitch out of
            // the strict BadHeadShot path. The tick controller still owns the
            // corrective pitch, so the whole mouse is never frozen.
            accumulatedDY = 0.0;
            return;
        }
        if (action != AimbotModule.MOUSE_CONSUME) return;
        accumulatedDX = 0.0;
        accumulatedDY = 0.0;
        callbackInfo.cancel();
    }
}
