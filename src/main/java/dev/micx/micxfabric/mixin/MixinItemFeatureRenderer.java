package dev.micx.micxfabric.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;
import dev.micx.micxfabric.ZombieFadeItemQuads;
import dev.micx.micxfabric.chams.ChamsSeedTypes;
import dev.micx.micxfabric.chams.DepthSeedItemQuads;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 回放阶段的手持淡化与 Chams 种子处理。
 *
 * <p>ZombieFade：quads 为 {@link ZombieFadeItemQuads} 标记时——
 * <ul>
 *   <li>非 TRANSLUCENT 层把 RenderType 换成 itemTranslucent（同图集），alpha 混合才成立；</li>
 *   <li>注入点必须落在 {@code QuadInstance.setColor} 上：vanilla 对未染色 quad 强制传 -1
 *       （getLayerColorSafe 无视 tintLayers 数组直接返回 -1），剑等无染色物品不在此注 alpha
 *       就永远不会淡化；已染色层保留原 RGB 只压 alpha。</li>
 * </ul>
 * 非 Chams 实体 100% vanilla；种子播种阶段全部让行。
 */
@Mixin(ItemFeatureRenderer.class)
public class MixinItemFeatureRenderer {

    @Redirect(
            method = "prepareMainSubmit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/model/geometry/BakedQuad$MaterialInfo;itemRenderType()Lnet/minecraft/client/renderer/rendertype/RenderType;"
            )
    )
    private RenderType micx$itemRenderType(BakedQuad.MaterialInfo material,
                                           ItemFeatureRenderer.Submit submit) {
        if (submit.quads() instanceof DepthSeedItemQuads) {
            return ChamsSeedTypes.seedType(material.sprite().atlasLocation());
        }
        if (submit.quads() instanceof ZombieFadeItemQuads
                && material.layer() != ChunkSectionLayer.TRANSLUCENT) {
            return RenderTypes.itemTranslucent(material.sprite().atlasLocation());
        }
        return material.itemRenderType();
    }

    @Redirect(
            method = "prepareMainSubmit",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/QuadInstance;setColor(I)V"
            )
    )
    private void micx$itemColor(QuadInstance quadInstance, int color,
                                ItemFeatureRenderer.Submit submit) {
        if (submit.quads() instanceof ZombieFadeItemQuads fade) {
            int origAlpha = color == -1 ? 255 : (color >>> 24) & 0xFF;
            int rgb = color == -1 ? 0x00FFFFFF : (color & 0x00FFFFFF);
            int alpha = Math.min(origAlpha, fade.fadeAlpha());
            quadInstance.setColor((alpha << 24) | rgb);
            return;
        }
        quadInstance.setColor(color);
    }

    @Inject(method = "prepareFoilSubmit", at = @At("HEAD"), cancellable = true)
    private void micx$skipDepthSeedAndFadeFoil(ItemFeatureRenderer.Submit submit, CallbackInfo ci) {
        // 种子阶段：光效通道无用；淡化武器：取消附魔贴花，整只均匀半透明；@Inject 必须用 CallbackInfo
        // 种子阶段：光效通道无用；淡化武器：取消附魔贴花，整只均匀半透明
        if (submit.quads() instanceof DepthSeedItemQuads || submit.quads() instanceof ZombieFadeItemQuads) {
            ci.cancel();
        }
    }
}
