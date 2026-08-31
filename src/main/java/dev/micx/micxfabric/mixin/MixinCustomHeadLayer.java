package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.MicxRenderKeys;
import dev.micx.micxfabric.ZombieFadeHeadContext;
import dev.micx.micxfabric.ZombieFadeModule;
import dev.micx.micxfabric.chams.ChamsSeedTypes;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.SkullBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 种子阶段拦截头颅层 RenderType 解析点；
 * ZombieFade 在此设置佩戴头颅的淡化窗口（供 MixinSkullBlockRenderer 读取）。
 * 种子阶段不设窗——与本体淡化同优先级（Chams 播种通道永不淡化）。
 */
@Mixin(CustomHeadLayer.class)
public class MixinCustomHeadLayer {
    @Shadow
    private PlayerSkinRenderCache playerSkinRenderCache;

    @Unique
    private static final String MICX_SUBMIT_SIG =
            "submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I"
                    + "Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V";

    @Inject(method = MICX_SUBMIT_SIG, at = @At("HEAD"))
    private void micx$fadeHeadCapture(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                      LivingEntityRenderState state, float yRot, float xRot,
                                      CallbackInfo ci) {
        ZombieFadeHeadContext.clear();
        if (ChamsSeedTypes.ACTIVE) return;
        LivingEntity entity = state.getData(MicxRenderKeys.ENTITY);
        if (entity != null && ZombieFadeModule.instance().shouldFade(entity)) {
            ZombieFadeHeadContext.set(entity);
        }
    }

    @Inject(method = MICX_SUBMIT_SIG, at = @At("RETURN"))
    private void micx$fadeHeadRelease(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                      LivingEntityRenderState state, float yRot, float xRot,
                                      CallbackInfo ci) {
        ZombieFadeHeadContext.clear();
    }

    @Inject(method = "resolveSkullRenderType", at = @At("HEAD"), cancellable = true)
    private void micx$chamsSkullType(LivingEntityRenderState state, SkullBlock.Type type,
                                     CallbackInfoReturnable<RenderType> cir) {
        if (!ChamsSeedTypes.ACTIVE || state.wornHeadProfile == null) return;
        PlayerSkinRenderCache.RenderInfo info = playerSkinRenderCache.getOrDefault(state.wornHeadProfile);
        if (info == null) return;
        Identifier texture = info.playerSkin().body().texturePath();
        if (texture == null) return;
        cir.setReturnValue(ChamsSeedTypes.seedType(texture));
    }
}
