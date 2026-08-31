package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.ParticleFreeModule;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NoParticles：粒子全隐。{@code add(Particle)} 是客户端一切粒子入列的唯一漏斗
 * （26.2"最少"档仍强制渲染爆炸等关键粒子），在此整段取消即零粒子；
 * {@code createTrackingEmitter} 提前取消，避免追踪发射器空转。
 */
@Mixin(ParticleEngine.class)
public class MixinParticleEngine {

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void micx$blockAdd(Particle particle, CallbackInfo ci) {
        if (!ParticleFreeModule.instance().enabled()) return;
        ci.cancel();
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V",
            at = @At("HEAD"), cancellable = true)
    private void micx$blockEmitter1(Entity entity, ParticleOptions options, CallbackInfo ci) {
        if (!ParticleFreeModule.instance().enabled()) return;
        ci.cancel();
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V",
            at = @At("HEAD"), cancellable = true)
    private void micx$blockEmitter2(Entity entity, ParticleOptions options, int lifetime, CallbackInfo ci) {
        if (!ParticleFreeModule.instance().enabled()) return;
        ci.cancel();
    }
}
