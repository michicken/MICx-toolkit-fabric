package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.ZoomScopeModule;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MixinMouseHandlerZoom {

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void micx$zoomScaleTurn(double time, CallbackInfo ci) {
        if (!ZoomScopeModule.instance().isActive()) return;
        float factor = ZoomScopeModule.instance().state().sensitivityFactor();
        if (factor >= 0.999f && factor <= 1.001f) return;
        accumulatedDX *= factor;
        accumulatedDY *= factor;
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void micx$zoomScrollIntercept(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (ZoomScopeModule.instance().onScroll(yOffset)) {
            ci.cancel();
        }
    }
}
