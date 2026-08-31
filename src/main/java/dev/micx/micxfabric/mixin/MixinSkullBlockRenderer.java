package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.ZombieFadeHeadContext;
import dev.micx.micxfabric.ZombieFadeModule;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * ZombieFade：佩戴头颅随本体淡化。
 *
 * <p>26.2 玩家头颅（PLAYER + profile）默认走 entityTranslucent，混合能力已具备；
 * 但 SkullBlockRenderer.submitSkull 内部用"无 tint 重载"submitModel
 * （light/overlay/outlineColor），头颅永远白模不透明——把该提交换成带 tint 重载，
 * 注入 fade alpha（0x4DFFFFFF）。非 Chams 实体 100% vanilla；窗口由
 * MixinCustomHeadLayer 设置（种子阶段不设窗，与本体淡化同优先级）。
 */
@Mixin(SkullBlockRenderer.class)
public class MixinSkullBlockRenderer {

    @Redirect(
            method = "submitSkull(FLcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I"
                    + "Lnet/minecraft/client/model/object/skull/SkullModelBase;"
                    + "Lnet/minecraft/client/renderer/rendertype/RenderType;I"
                    + "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel("
                            + "Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/rendertype/RenderType;"
                            + "IIILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void micx$skullSubmitTint(SubmitNodeCollector collector, Model model, Object state,
                                             PoseStack poseStack, RenderType type, int light, int overlay,
                                             int outlineColor, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        LivingEntity faded = ZombieFadeHeadContext.get();
        if (faded == null || !ZombieFadeModule.instance().shouldFade(faded)) {
            collector.submitModel(model, state, poseStack, type, light, overlay, outlineColor, crumbling);
            return;
        }
        // 白底只压 alpha：头颅无原 tint，语义等同 vanilla -1 但半透明
        int tint = ZombieFadeModule.fadedTint(-1);
        collector.submitModel(model, state, poseStack, type, light, overlay, tint, null, outlineColor, crumbling);
    }
}
