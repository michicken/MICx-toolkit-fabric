package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.PlayerOutlineEspModule;
import dev.micx.micxfabric.PlayerVisibilityModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies PlayerVisibility hide mode before entity extraction submits a render state. */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {
    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private <E extends Entity> void micx$hideNearbyPlayers(
            E entity, Frustum frustum, double x, double y, double z,
            CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = Minecraft.getInstance();
        if (entity instanceof AbstractClientPlayer player
                && PlayerVisibilityModule.shouldHide(player, client)
                && !PlayerOutlineEspModule.shouldOutline(player, client)) {
            PlayerVisibilityModule.diagCancel("dispatcher", player);
            cir.setReturnValue(false);
        }
    }
}
