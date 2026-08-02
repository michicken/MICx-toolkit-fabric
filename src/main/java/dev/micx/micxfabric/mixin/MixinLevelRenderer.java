package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.PlayerOutlineEspModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Enables the vanilla entity-outline composite when MICx has a player target. */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    @Inject(
            method = "submitEntities",
            at = @At("RETURN")
    )
    private void micx$enablePlayerOutlines(
            PoseStack poseStack,
            LevelRenderState state,
            SubmitNodeCollector collector,
            CallbackInfo callbackInfo
    ) {
        Minecraft client = Minecraft.getInstance();
        boolean vanillaOutline = false;
        for (EntityRenderState renderState : state.entityRenderStates) {
            if (renderState.appearsGlowing()) {
                vanillaOutline = true;
                break;
            }
        }
        state.shouldShowEntityOutlines = vanillaOutline || PlayerOutlineEspModule.hasTarget(client);
    }
}
