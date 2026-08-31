package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.ChamsModule;
import dev.micx.micxfabric.MicxRenderKeys;
import dev.micx.micxfabric.PlayerOutlineEspModule;
import dev.micx.micxfabric.PlayerVisibilityModule;
import dev.micx.micxfabric.ZombieFadeModule;
import dev.micx.micxfabric.chams.ChamsSeedTypes;
import dev.micx.micxfabric.chams.MicxFixedOrderCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Chams v6 深度种子双通道：
 * submit HEAD 时先以 order(-1) 递归提交一遍实体（期间 ACTIVE=true，
 * 所有层经 MixinChamsRenderTypes/MixinItemFeatureRenderer 换成种子类型，
 * 只把被墙挡住部分的深度种进缓冲），随后外层原样提交用 vanilla GEQUAL
 * 重建正确表面——穿墙 + 自遮挡 100% 原生。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {
    private static final Map<LivingEntityRenderState, AbstractClientPlayer> MICX_PLAYERS = new WeakHashMap<>();

    @Unique
    private boolean micx$seedSubmitting;

    @Shadow
    protected abstract Identifier getTextureLocation(LivingEntityRenderState state);

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void micx$rememberPlayer(LivingEntity entity, LivingEntityRenderState state,
                                     float partialTick, CallbackInfo callbackInfo) {
        state.setData(MicxRenderKeys.ENTITY, entity);
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

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void micx$hideNearbyPlayerSubmit(LivingEntityRenderState state, PoseStack poseStack,
                                             SubmitNodeCollector collector, CameraRenderState camera,
                                             CallbackInfo ci) {
        // PlayerVisibility hide 模式（对齐 Forge RenderPlayerEvent.Pre.setCanceled）：
        // 整个玩家渲染（肉身/盔甲/手持/名牌）一次取消。shouldRender 侧拦截（MixinEntityRenderDispatcher）
        // 若未命中，这里是保底。ESP 描边的玩家不隐藏。
        LivingEntity entity = state.getData(MicxRenderKeys.ENTITY);
        if (entity instanceof AbstractClientPlayer player) {
            Minecraft client = Minecraft.getInstance();
            if (PlayerVisibilityModule.shouldHide(player, client)
                    && !PlayerOutlineEspModule.shouldOutline(player, client)) {
                PlayerVisibilityModule.diagCancel("submit", player);
                ci.cancel();
            }
        }
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"))
    private void micx$chamsSeedSubmit(LivingEntityRenderState state, PoseStack poseStack,
                                      SubmitNodeCollector collector, CameraRenderState camera,
                                      CallbackInfo ci) {
        LivingEntity entity = state.getData(MicxRenderKeys.ENTITY);
        boolean chamsTarget = entity != null
                && ChamsModule.instance().shouldApply(entity, Minecraft.getInstance(), false);
        if (chamsTarget && !micx$seedSubmitting) {
            micx$seedSubmitting = true;
            ChamsSeedTypes.ACTIVE = true;
            try {
                SubmitNodeCollector seedCollector =
                        new MicxFixedOrderCollector(collector.order(-1));
                ((LivingEntityRenderer) (Object) this).submit(state, poseStack, seedCollector, camera);
            } finally {
                ChamsSeedTypes.ACTIVE = false;
                micx$seedSubmitting = false;
            }
        } else {
            ChamsSeedTypes.ACTIVE = chamsTarget && micx$seedSubmitting;
        }
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("RETURN"))
    private void micx$chamsSubmitEnd(LivingEntityRenderState state, PoseStack poseStack,
                                     SubmitNodeCollector collector, CameraRenderState camera,
                                     CallbackInfo ci) {
        ChamsSeedTypes.ACTIVE = false;
    }

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void micx$chamsSeedType(LivingEntityRenderState state, boolean bodyVisible,
                                    boolean translucent, boolean glowing,
                                    CallbackInfoReturnable<RenderType> cir) {
        LivingEntity entity = state.getData(MicxRenderKeys.ENTITY);
        if (ChamsSeedTypes.ACTIVE && entity != null
                && ChamsModule.instance().shouldApply(entity, Minecraft.getInstance(), false)) {
            cir.setReturnValue(ChamsSeedTypes.seedType(getTextureLocation(state)));
            return;
        }
        // 种子阶段已返回，以上不再走淡化
        if (ChamsSeedTypes.ACTIVE) return;
        // ZombieFade：本体必须切 translucent 才会读 alpha（与 PlayerVisibility 同路）
        if (entity != null && ZombieFadeModule.instance().shouldFade(entity)) {
            cir.setReturnValue(RenderTypes.entityTranslucent(getTextureLocation(state)));
            return;
        }
        AbstractClientPlayer player;
        synchronized (MICX_PLAYERS) {
            player = MICX_PLAYERS.get(state);
        }
        if (player != null) {
            int tint = PlayerVisibilityModule.modelTint(player, Minecraft.getInstance());
            if (tint != -1) {
                cir.setReturnValue(RenderTypes.entityTranslucent(getTextureLocation(state)));
            }
        }
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

    @Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true)
    private void micx$applyZombieFade(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
        LivingEntity entity = state.getData(MicxRenderKeys.ENTITY);
        if (entity == null || !ZombieFadeModule.instance().shouldFade(entity)) return;
        int orig = cir.getReturnValue();
        cir.setReturnValue(ZombieFadeModule.fadedTint(orig));
    }

    /* ---- ZombieFade hurtTime 屏蔽（FR-3：受击红片段级烘焙，须临时置 0） ---- */

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"))
    private void micx$fadeSuppressHurtHead(LivingEntityRenderState state,
                                           PoseStack poseStack,
                                           SubmitNodeCollector collector,
                                           CameraRenderState camera,
                                           CallbackInfo ci) {
        LivingEntity entity = state.getData(MicxRenderKeys.ENTITY);
        if (entity != null) ZombieFadeModule.instance().suppressHurt(entity);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("RETURN"))
    private void micx$fadeRestoreHurtReturn(LivingEntityRenderState state,
                                            PoseStack poseStack,
                                            SubmitNodeCollector collector,
                                            CameraRenderState camera,
                                            CallbackInfo ci) {
        LivingEntity entity = state.getData(MicxRenderKeys.ENTITY);
        if (entity != null) ZombieFadeModule.instance().restoreHurt(entity);
    }
}
