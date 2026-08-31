package dev.micx.micxfabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.micx.micxfabric.MicxRenderKeys;
import dev.micx.micxfabric.ZombieFadeMarkingCollector;
import dev.micx.micxfabric.ZombieFadeModule;
import dev.micx.micxfabric.chams.ChamsSeedTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * ZombieFade：手持随本体淡化。
 *
 * <p>提交与回放分离——submitArmWithItem 只是把 quads/资源排队，真正消费在稍后的
 * ItemFeatureRenderer（此时 ThreadLocal 窗口早已关闭）。因此不能只设 ThreadLocal：
 * 在 {@code ItemStackRenderState.submit} 调用处把 collector 换成
 * {@link ZombieFadeMarkingCollector}，让进入队列的 quads 携带 fade alpha 标记，
 * 回放阶段凭标记识别（非 Chams 实体 100% vanilla；种子播种阶段不换）。
 */
@Mixin(ItemInHandLayer.class)
public abstract class MixinItemInHandZombieFade {

    @Redirect(
            method = "submitArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
            )
    )
    private void micx$markFadeSubmits(ItemStackRenderState itemState,
                                      PoseStack poseStack,
                                      SubmitNodeCollector collector,
                                      int light, int overlay, int outlineColor,
                                      ArmedEntityRenderState state,
                                      ItemStackRenderState outerItemState,
                                      ItemStack stack,
                                      HumanoidArm arm,
                                      PoseStack outerPoseStack,
                                      SubmitNodeCollector outerCollector,
                                      int outerLight) {
        LivingEntity entity = state == null ? null : state.getData(MicxRenderKeys.ENTITY);
        boolean fade = !ChamsSeedTypes.ACTIVE
                && ZombieFadeModule.instance().enabled()
                && entity != null
                && ZombieFadeModule.instance().shouldFade(entity);
        if (!fade) {
            itemState.submit(poseStack, collector, light, overlay, outlineColor);
            return;
        }
        int alpha = Math.max(0, Math.min(255,
                Math.round(ZombieFadeModule.instance().alpha() * 255f)));
        itemState.submit(poseStack, new ZombieFadeMarkingCollector(collector, alpha),
                light, overlay, outlineColor);
    }
}
