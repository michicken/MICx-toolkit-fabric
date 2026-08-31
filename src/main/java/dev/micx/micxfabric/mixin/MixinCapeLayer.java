package dev.micx.micxfabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.micx.micxfabric.MicxRenderKeys;
import dev.micx.micxfabric.PlayerVisibilityModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 披风层：PlayerVisibility fade 模式下披风随肉身一起半透明。
 * 26.2 的 CapeLayer 用 entitySolid（不透明）+ 无 tint 重载 submitModel
 * （light/overlay/outlineColor/crumbling），不吃肉身的 getRenderType/getModelTint——
 * 与盔甲同类问题，同款 ThreadLocal 捕获 + RenderType/重载替换修复。
 */
@Mixin(CapeLayer.class)
public abstract class MixinCapeLayer {

    @Unique
    private static final ThreadLocal<Integer> MICX_CAPE_TINT = new ThreadLocal<>();

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I"
            + "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
            at = @At("HEAD"))
    private void micx$captureCapeState(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                       AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
        MICX_CAPE_TINT.remove();
        LivingEntity entity = state.getData(MicxRenderKeys.ENTITY);
        if (entity instanceof AbstractClientPlayer player) {
            int tint = PlayerVisibilityModule.modelTint(player, Minecraft.getInstance());
            if (tint != -1) MICX_CAPE_TINT.set(tint);
        }
    }

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I"
            + "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
            at = @At("RETURN"))
    private void micx$releaseCapeState(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                       AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
        MICX_CAPE_TINT.remove();
    }

    /** fade 玩家的披风 entitySolid → entityTranslucent（alpha 融合前提）。 */
    @Redirect(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I"
            + "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;entitySolid("
                            + "Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType micx$capeRenderType(Identifier texture) {
        if (MICX_CAPE_TINT.get() != null) return RenderTypes.entityTranslucent(texture);
        return RenderTypes.entitySolid(texture);
    }

    /** 披风 submitModel 走的是无 tint 重载（light/overlay/outlineColor/crumbling）——
     *  fade 时换调带 tint 重载（sprite=null，outline 原样透传），压入 fade alpha。 */
    @Redirect(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I"
            + "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel("
                            + "Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/rendertype/RenderType;"
                            + "IIILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void micx$capeSubmitModel(SubmitNodeCollector collector, Model model, Object state,
                                      PoseStack poseStack, RenderType type, int light, int overlay,
                                      int outline, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        Integer tint = MICX_CAPE_TINT.get();
        if (tint != null) {
            collector.submitModel(model, state, poseStack, type, light, overlay, tint, null, outline, crumbling);
        } else {
            collector.submitModel(model, state, poseStack, type, light, overlay, outline, crumbling);
        }
    }
}
