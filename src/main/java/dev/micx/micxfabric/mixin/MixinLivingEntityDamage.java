package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.AimbotModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bridges the 26.2 client damage packet path to the migrated threat-memory rule. */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityDamage {
    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void micx$recordAimbotThreat(DamageSource source, CallbackInfo callbackInfo) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player != (Object) this || source == null) return;

        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity living) {
            AimbotModule.recordThreat(living);
            return;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof LivingEntity living) {
            AimbotModule.recordThreat(living);
        }
    }
}
