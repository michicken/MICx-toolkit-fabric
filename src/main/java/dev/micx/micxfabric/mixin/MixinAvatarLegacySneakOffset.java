package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.LegacySneakVisualsModule;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 第三人称：看别人/看自己潜行时的视觉下沉回退。
 *
 * <p>AvatarRenderer.getRenderOffset 在 isCrouching 时下移 y = scale*(-2/16)。
 * 1.8 潜行视觉只下沉约 0.08，对比现代 0.35，故潜行且模块开启时把该下沉消掉
 * （改回 0 偏移），使第三人称看起来像 1.8 的微蹲。碰撞不动。
 */
@Mixin(AvatarRenderer.class)
public abstract class MixinAvatarLegacySneakOffset {
    @Inject(method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"), cancellable = true)
    private void micx$legacySneakNoCrouchSink(AvatarRenderState state, CallbackInfoReturnable<Vec3> cir) {
        if (!LegacySneakVisualsModule.instance().enabled()) return;
        if (state == null || !state.isCrouching) return;
        Vec3 orig = cir.getReturnValue();
        if (orig == null) return;
        // 现代下沉约 -0.125*scale；视觉改回无下沉（等效抬 0.125*scale*16 ≈ 2 像素，对应眼高差 0.27 的模型侧分量）
        // 保持 x/z 不动，仅 y 回抬到与站立一致
        double lift = state.scale * (2.0 / 16.0);
        Vec3 fixed = new Vec3(orig.x, orig.y + lift, orig.z);
        cir.setReturnValue(fixed);
    }
}
