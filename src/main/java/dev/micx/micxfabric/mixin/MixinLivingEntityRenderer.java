package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.PlayerOutlineEspModule;
import dev.micx.micxfabric.PlayerVisibilityModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.WeakHashMap;

/** Supplies translucent player render types for PlayerVisibility opacity mode. */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {
    private static final Map<LivingEntityRenderState, AbstractClientPlayer> MICX_PLAYERS = new WeakHashMap<>();

    @Shadow
    protected abstract Identifier getTextureLocation(LivingEntityRenderState state);

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void micx$rememberPlayer(LivingEntity entity, LivingEntityRenderState state,
                                     float partialTick, CallbackInfo callbackInfo) {
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
}
