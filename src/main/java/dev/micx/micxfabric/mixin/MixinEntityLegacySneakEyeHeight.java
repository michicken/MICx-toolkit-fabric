package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.LegacySneakVisualsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.8 潜行眼高：把<b>本地玩家</b>的眼高真值抬回 1.8 的 1.54，使四处保持一致。
 *
 * <p><b>为什么必须改在这里，而不是只改相机/瞄算：</b>
 * <ul>
 *   <li>命中射线起点 = {@code Entity.getEyePosition()} = 位置 + 本方法返回值
 *       （{@code Minecraft.pick → LocalPlayer.raycastHitResult}）。</li>
 *   <li>而 {@code Minecraft.startAttack()} <b>只在 {@code hitResult} 命中实体时才发包</b>。</li>
 * </ul>
 * 若只在相机上做视觉补偿，本地眼高仍是现代的 1.27：瞄准头层时把 ray 抬到 1.54 会让
 * 本地 pick 从 1.27 打出而<b>越过头顶</b>（于是连攻击都不发）；维持 1.27 又会让服务端
 * （1.8.9 恒为 1.54）复核判空。两条路都错，唯一正解是把眼高真值本身对齐 —— 改这里后
 * 相机 / 本地 pick / aim 计算 / 服务端四方一致。
 *
 * <p>只改眼高，<b>不碰碰撞尺寸与 pose</b>（1.5 格缝照样能钻）；该字段不下发，
 * 服务端/反作弊/Via 不受影响。仅作用于本地玩家，对其他实体零影响。
 */
@Mixin(Entity.class)
public abstract class MixinEntityLegacySneakEyeHeight {
    @Shadow private float eyeHeight;
    @Shadow public abstract Pose getPose();

    @Inject(method = "getEyeHeight()F", at = @At("HEAD"), cancellable = true)
    private void micx$legacySneakEyeHeight(CallbackInfoReturnable<Float> cir) {
        if (!LegacySneakVisualsModule.instance().enabled()) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        if ((Object) this != client.player) return;
        if (getPose() != Pose.CROUCHING) return;
        cir.setReturnValue(LegacySneakVisualsModule.effectiveEyeHeight(true, true, eyeHeight));
    }
}
