package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.LegacySneakVisualsModule;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.8 潜行视觉：仅抬第一人称相机 Y（视觉），不改实体碰撞/pose（逻辑）。
 *
 * <p>26.2 原版对 eyeHeight 做了 0.5 系数的平滑插值（Camera.tick 内 lerp），
 * 导致潜行/起身有约 4 tick 的过渡动画——1.8 是瞬间切换。启用本模块时
 * 在 tick 尾部把 eyeHeight/eyeHeightOld 直接 snap 到视觉目标：
 * CROUCHING → 1.54（对齐 1.8），STANDING → 1.62，消除动画与闪动。
 * 不写回 entity eyeHeight，不影响服务端/反作弊/Via，1.5 格缝照样能钻。
 */
@Mixin(Camera.class)
public abstract class MixinCameraLegacySneak {
    @Shadow private Entity entity;
    @Shadow private float eyeHeight;
    @Shadow private float eyeHeightOld;

    @Inject(method = "tick", at = @At("RETURN"))
    private void micx$legacySneakSnapEye(CallbackInfo ci) {
        if (!LegacySneakVisualsModule.instance().enabled()) return;
        if (entity == null) return;
        Pose pose = entity.getPose();
        if (pose == Pose.CROUCHING) {
            eyeHeight = LegacySneakVisualsModule.VISUAL_SNEAK_EYE_HEIGHT;
            eyeHeightOld = LegacySneakVisualsModule.VISUAL_SNEAK_EYE_HEIGHT;
        } else if (pose == Pose.STANDING) {
            // 起身也瞬间回弹，消除 1.62 的回程动画（与 1.8 一致）
            float standingEye = entity.getEyeHeight();
            eyeHeight = standingEye;
            eyeHeightOld = standingEye;
        }
    }
}
