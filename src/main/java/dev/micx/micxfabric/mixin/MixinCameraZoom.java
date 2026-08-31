package dev.micx.micxfabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.micx.micxfabric.ZoomScopeModule;
import dev.micx.micxfabric.ZoomScopeState;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public abstract class MixinCameraZoom {

    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float micx$zoomFov(float original) {
        if (!ZoomScopeModule.instance().isActive()) return original;
        int zoom = ZoomScopeModule.instance().state().zoom();
        if (zoom <= 1) return original;
        double fovRad = Math.toRadians(original);
        double zoomed = ZoomScopeState.zoomedFovRad(fovRad, zoom);
        return (float) Math.toDegrees(zoomed);
    }
}
