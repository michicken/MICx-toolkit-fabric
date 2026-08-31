package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.MicxRenderKeys;
import dev.micx.micxfabric.PlayerVisibilityModule;
import dev.micx.micxfabric.ZombieFadeModule;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 盔甲/装备层：
 * - Chams：armorCutoutNoCull → ChamsRenderTypes（原贴图 + 管线级原生深度偏移，纯透视不覆色）；
 * - ZombieFade：armorCutoutNoCull → entityTranslucent 变体，submitModel tint 高位注入 fade alpha
 *   （对齐 Forge FadeArmorLayer：盔甲/头盔随肉身一起淡化）；
 * - 非 chams/fade 实体 100% vanilla。
 */
@Mixin(EquipmentLayerRenderer.class)
public abstract class MixinEquipmentLayerRenderer {

    @Unique
    private static final ThreadLocal<LivingEntity> MICX_EQUIP_ENTITY = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<Boolean> MICX_EQUIP_IS_FADE = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<Identifier> MICX_EQUIP_TEXTURE = new ThreadLocal<>();

    /** PlayerVisibility fade：非 null 时 armor tint 整体替换为该值（alpha + 原 RGB 由调用方合成）。 */
    @Unique
    private static final ThreadLocal<Integer> MICX_EQUIP_PV_TINT = new ThreadLocal<>();

    @Inject(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
            + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
            + "Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I"
            + "Lnet/minecraft/resources/Identifier;II)V",
            at = @At("HEAD"))
    private void micx$captureEntity(EquipmentClientInfo.LayerType layerType,
                                    ResourceKey<?> assetKey,
                                    Model<?> model,
                                    Object state,
                                    ItemStack stack,
                                    PoseStack poseStack,
                                    SubmitNodeCollector collector,
                                    int seed,
                                    Identifier texture,
                                    int light,
                                    int overlay,
                                    CallbackInfo ci) {
        MICX_EQUIP_ENTITY.set(state instanceof LivingEntityRenderState living
                ? living.getData(MicxRenderKeys.ENTITY) : null);
        MICX_EQUIP_IS_FADE.remove();
        MICX_EQUIP_PV_TINT.remove();
        MICX_EQUIP_TEXTURE.set(texture);
    }

    @Inject(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
            + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
            + "Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I"
            + "Lnet/minecraft/resources/Identifier;II)V",
            at = @At("RETURN"))
    private void micx$releaseEntity(EquipmentClientInfo.LayerType layerType,
                                    ResourceKey<?> assetKey,
                                    Model<?> model,
                                    Object state,
                                    ItemStack stack,
                                    PoseStack poseStack,
                                    SubmitNodeCollector collector,
                                    int seed,
                                    Identifier texture,
                                    int light,
                                    int overlay,
                                    CallbackInfo ci) {
        MICX_EQUIP_ENTITY.remove();
        MICX_EQUIP_IS_FADE.remove();
        MICX_EQUIP_PV_TINT.remove();
        MICX_EQUIP_TEXTURE.remove();
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
        LivingEntity entity = MICX_EQUIP_ENTITY.get();
        if (entity != null) {
            // PlayerVisibility fade：玩家盔甲随肉身一起半透明（对齐 Forge GlStateManager 全局 alpha）
            if (entity instanceof AbstractClientPlayer player) {
                int pvTint = PlayerVisibilityModule.modelTint(player, Minecraft.getInstance());
                if (pvTint != -1) {
                    MICX_EQUIP_IS_FADE.remove();
                    MICX_EQUIP_PV_TINT.set(pvTint);
                    return RenderTypes.entityTranslucent(texture);
                }
            }
            if (ZombieFadeModule.instance().shouldFade(entity)) {
                MICX_EQUIP_IS_FADE.set(Boolean.TRUE);
                return RenderTypes.entityTranslucent(texture);
            }
        }
        return RenderTypes.armorCutoutNoCull(texture);
    }

    /** fade 实体的盔甲提交把 tint 高位替换为 fade alpha；其余提交原样透传。
     *  PlayerVisibility：保留盔甲原 RGB（染色甲不褪色），只压 alpha（Forge color(1,1,1,a) 语义）。 */
    @Redirect(
            method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
                    + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
                    + "Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I"
                    + "Lnet/minecraft/resources/Identifier;II)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel("
                            + "Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/rendertype/RenderType;"
                            + "IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;"
                            + "ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void micx$armorSubmitModel(OrderedSubmitNodeCollector collector, Model model, Object state,
                                       PoseStack poseStack, RenderType type, int light, int overlay,
                                       int tint, TextureAtlasSprite sprite, int outline,
                                       ModelFeatureRenderer.CrumblingOverlay crumbling) {
        Integer pvTint = MICX_EQUIP_PV_TINT.get();
        if (pvTint != null) {
            tint = (pvTint & 0xFF000000) | (tint & 0x00FFFFFF);
        } else if (Boolean.TRUE.equals(MICX_EQUIP_IS_FADE.get())) {
            tint = ZombieFadeModule.instance().armorTint(tint);
        }
        collector.submitModel(model, state, poseStack, type, light, overlay, tint, sprite, outline, crumbling);
    }
}
