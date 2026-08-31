package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.chams.ChamsSeedTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 种子阶段（ChamsSeedTypes.ACTIVE）把实体/盔甲/物品各层请求的 RenderType
 * 统一换成深度种子类型，让整只实体（含盔甲、手持物）一起参与深度播种。
 * 用各层自己的贴图，外观正常，只是深度语义换成 LESS_THAN 种子。
 */
@Mixin(RenderTypes.class)
public class MixinChamsRenderTypes {

    @Inject(method = "armorCutoutNoCull", at = @At("HEAD"), cancellable = true)
    private static void micx$armorCutoutNoCull(Identifier texture, CallbackInfoReturnable<RenderType> cir) {
        if (ChamsSeedTypes.ACTIVE) cir.setReturnValue(ChamsSeedTypes.seedType(texture));
    }

    @Inject(method = "armorTranslucent", at = @At("HEAD"), cancellable = true)
    private static void micx$armorTranslucent(Identifier texture, CallbackInfoReturnable<RenderType> cir) {
        if (ChamsSeedTypes.ACTIVE) cir.setReturnValue(ChamsSeedTypes.seedType(texture));
    }

    @Inject(method = "entitySolid", at = @At("HEAD"), cancellable = true)
    private static void micx$entitySolid(Identifier texture, CallbackInfoReturnable<RenderType> cir) {
        if (ChamsSeedTypes.ACTIVE) cir.setReturnValue(ChamsSeedTypes.seedType(texture));
    }

    @Inject(method = "entityCutoutCull", at = @At("HEAD"), cancellable = true)
    private static void micx$entityCutoutCull(Identifier texture, CallbackInfoReturnable<RenderType> cir) {
        if (ChamsSeedTypes.ACTIVE) cir.setReturnValue(ChamsSeedTypes.seedType(texture));
    }

    @Inject(method = "entityCutoutZOffset(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            at = @At("HEAD"), cancellable = true)
    private static void micx$entityCutoutZOffset(Identifier texture, boolean affectsOutline,
                                                 CallbackInfoReturnable<RenderType> cir) {
        if (ChamsSeedTypes.ACTIVE) cir.setReturnValue(ChamsSeedTypes.seedType(texture));
    }

    @Inject(method = "entityCutout(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            at = @At("HEAD"), cancellable = true)
    private static void micx$entityCutout(Identifier texture, boolean affectsOutline,
                                          CallbackInfoReturnable<RenderType> cir) {
        if (ChamsSeedTypes.ACTIVE) cir.setReturnValue(ChamsSeedTypes.seedType(texture));
    }

    @Inject(method = "entityTranslucent(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            at = @At("HEAD"), cancellable = true)
    private static void micx$entityTranslucent(Identifier texture, boolean affectsOutline,
                                               CallbackInfoReturnable<RenderType> cir) {
        if (ChamsSeedTypes.ACTIVE) cir.setReturnValue(ChamsSeedTypes.seedType(texture));
    }

    @Inject(method = "itemCutout", at = @At("HEAD"), cancellable = true)
    private static void micx$itemCutout(Identifier texture, CallbackInfoReturnable<RenderType> cir) {
        if (ChamsSeedTypes.ACTIVE) cir.setReturnValue(ChamsSeedTypes.seedType(texture));
    }

    @Inject(method = "itemTranslucent", at = @At("HEAD"), cancellable = true)
    private static void micx$itemTranslucent(Identifier texture, CallbackInfoReturnable<RenderType> cir) {
        if (ChamsSeedTypes.ACTIVE) cir.setReturnValue(ChamsSeedTypes.seedType(texture));
    }

    @Inject(method = "entityTranslucentCullItemTarget", at = @At("HEAD"), cancellable = true)
    private static void micx$entityTranslucentCullItemTarget(Identifier texture,
                                                             CallbackInfoReturnable<RenderType> cir) {
        if (ChamsSeedTypes.ACTIVE) cir.setReturnValue(ChamsSeedTypes.seedType(texture));
    }
}
