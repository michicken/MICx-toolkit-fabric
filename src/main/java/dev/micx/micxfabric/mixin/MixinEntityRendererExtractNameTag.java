package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.ZombiesExplorerModule;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ZE NameTag: inject into vanilla extractNameTags to set state.nameTag for marked mobs. */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererExtractNameTag<T extends Entity, S extends EntityRenderState> {

    @Inject(method = "extractNameTags", at = @At("TAIL"))
    private void micx$zeNameTag(T entity, S state, float partialTick, CallbackInfo ci) {
        if (!(entity instanceof LivingEntity le)) return;
        ZombiesExplorerModule ze = ZombiesExplorerModule.instance();
        if (!ze.enabled() || !ze.getNameTag()) return;
        if (state.nameTag != null) return; // don't override vanilla name
        Component tag = classifyNameTag(le, ze);
        if (tag != null) state.nameTag = tag;
    }

    private static Component classifyNameTag(LivingEntity e, ZombiesExplorerModule ze) {
        var tracker = ze.tracker;
        // Priority same as box
        if (tracker.powerupPredict.contains(e)) return Component.literal("§c§mPowerup");
        if (tracker.powerupEnsured.contains(e)) return Component.literal("§4Powerup");
        var anchor = tracker.badHeadshotAnchor();
        if (anchor != null && anchor.equals(e)) return Component.literal("§aBad Headshot");
        if (ze.getBadHeadShotOnLine() && tracker.entitiesOnLine.contains(e)) return Component.literal("§eBad Headshot");
        return null;
    }
}
