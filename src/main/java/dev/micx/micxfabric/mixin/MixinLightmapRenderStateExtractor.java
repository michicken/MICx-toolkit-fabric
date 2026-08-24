package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.FullbrightRuntime;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class MixinLightmapRenderStateExtractor {
    @Inject(
            method = "extract(Lnet/minecraft/client/renderer/state/LightmapRenderState;F)V",
            at = @At("TAIL")
    )
    private void micx$applyFullbright(LightmapRenderState state, float partialTick,
                                      CallbackInfo callbackInfo) {
        FullbrightRuntime.apply(state);
    }
}
