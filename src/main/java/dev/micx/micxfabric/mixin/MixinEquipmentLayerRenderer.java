package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.ChamsModule;
import dev.micx.micxfabric.ChamsRenderTypes;
import dev.micx.micxfabric.MicxRenderKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
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
 * Chams 盔甲/装备层：vanilla 身体走 LivingEntityRenderer.submit 的 Redirect，
 * 但盔甲由 EquipmentLayerRenderer 自行选 RenderType（armorCutoutNoCull）。
 * 这里在 renderLayers 期间用线程局部记录当前实体，把 armorCutoutNoCull
 * 换成同贴图的 chams 管线，使盔甲与肉身一起穿墙。
 */
@Mixin(EquipmentLayerRenderer.class)
public abstract class MixinEquipmentLayerRenderer {

    @Unique
    private static final ThreadLocal<LivingEntity> MICX_CHAMS_ENTITY = new ThreadLocal<>();

    // 处理器取目标方法前 4 个参数（第 4 个是 render state），Mixin 允许从头开始的连续子集
    @Inject(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
            + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
            + "Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I"
            + "Lnet/minecraft/resources/Identifier;II)V",
            at = @At("HEAD"))
    private void micx$captureEntity(Object layerType, Object assetKey, Object model, Object state,
                                    CallbackInfo ci) {
        MICX_CHAMS_ENTITY.set(state instanceof LivingEntityRenderState living
                ? living.getData(MicxRenderKeys.ENTITY) : null);
    }

    @Inject(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
            + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
            + "Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I"
            + "Lnet/minecraft/resources/Identifier;II)V",
            at = @At("RETURN"))
    private void micx$releaseEntity(CallbackInfo ci) {
        MICX_CHAMS_ENTITY.remove();
    }

    @Redirect(
            method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
                    + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
                    + "Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I"
                    + "Lnet/minecraft/resources/Identifier;II)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull("
                            + "Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType micx$chamsArmor(Identifier texture) {
        LivingEntity entity = MICX_CHAMS_ENTITY.get();
        if (entity != null && ChamsModule.instance().shouldApply(entity, Minecraft.getInstance(), false)) {
            RenderType chamsType = ChamsRenderTypes.entity(texture);
            if (chamsType != null) return chamsType;
        }
        return RenderTypes.armorCutoutNoCull(texture);
    }
}
