package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.ChamsModule;
import dev.micx.micxfabric.ChamsRenderTypes;
import dev.micx.micxfabric.PlayerOutlineEspModule;
import dev.micx.micxfabric.PlayerVisibilityModule;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.WeakHashMap;

/** Supplies translucent player render types for PlayerVisibility opacity mode. */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {
    private static final Map<LivingEntityRenderState, AbstractClientPlayer> MICX_PLAYERS = new WeakHashMap<>();
    private static final RenderStateDataKey<LivingEntity> MICX_ENTITY =
            RenderStateDataKey.create(() -> "micx_entity");

    @Shadow
    protected abstract Identifier getTextureLocation(LivingEntityRenderState state);

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void micx$rememberPlayer(LivingEntity entity, LivingEntityRenderState state,
                                     float partialTick, CallbackInfo callbackInfo) {
        state.setData(MICX_ENTITY, entity);
        synchronized (MICX_PLAYERS) {
            if (entity instanceof AbstractClientPlayer player) {
                MICX_PLAYERS.put(state, player);
                if (PlayerOutlineEspModule.shouldOutline(player, Minecraft.getInstance())) {
                    state.outlineColor = 0xFF19A85B;
                }
            } else {
                MICX_PLAYERS.remove(state);
            }
        }
    }

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void micx$useTranslucentPlayerType(LivingEntityRenderState state, boolean bodyVisible,
                                                boolean translucent, boolean glowing,
                                                CallbackInfoReturnable<RenderType> cir) {
        AbstractClientPlayer player;
        synchronized (MICX_PLAYERS) {
            player = MICX_PLAYERS.get(state);
        }
        if (player == null) return;
        Minecraft client = Minecraft.getInstance();
        int tint = PlayerVisibilityModule.modelTint(player, client);
        if (tint == -1) return;
        cir.setReturnValue(RenderTypes.entityTranslucent(getTextureLocation(state)));
    }

    @Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true)
    private void micx$applyPlayerOpacity(LivingEntityRenderState state,
                                         CallbackInfoReturnable<Integer> cir) {
        AbstractClientPlayer player;
        synchronized (MICX_PLAYERS) {
            player = MICX_PLAYERS.get(state);
        }
        if (player == null) return;
        int tint = PlayerVisibilityModule.modelTint(player, Minecraft.getInstance());
        if (tint != -1) cir.setReturnValue(tint);
    }

    /** Replaces only the vanilla body submit; layers keep their own textures and render types. */
    @Redirect(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel("
                    + "Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/rendertype/RenderType;"
                    + "IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;"
                    + "ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void micx$submitBodyModel(SubmitNodeCollector collector, Model model, Object renderState,
                                      PoseStack poseStack, RenderType vanillaType, int light, int overlay,
                                      int tintedColor, TextureAtlasSprite sprite, int outlineColor,
                                      ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        RenderType type = vanillaType;
        if (renderState instanceof LivingEntityRenderState state) {
            LivingEntity entity = state.getData(MICX_ENTITY);
            if (ChamsModule.instance().shouldApply(entity, Minecraft.getInstance(), false)) {
                RenderType chamsType = ChamsRenderTypes.entity(getTextureLocation(state));
                if (chamsType != null) type = chamsType;
            }
        }
        collector.submitModel(model, renderState, poseStack, type, light, overlay, tintedColor, sprite,
                outlineColor, crumblingOverlay);
    }
}
