package dev.micx.micxfabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.micx.micxfabric.SwordBlockModule;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 BLOCK 分支变换后追加 1.7 风格偏移。
 *
 * <p>FDPClient OneSevenAnimation 区别于 vanilla BLOCK 的全部差异是
 * 一次额外的 translate(-0.5, 0.2, 0)（Animations.kt line 172）。
 *
 * <p>hook 点：submitArmWithItem 内 BLOCK 分支最后一个 mulPose（ordinal=7）之后。
 * 由于 MixinItemStack 让剑返回 BLOCK，且 vanilla BLOCK 分支跳过 ShieldItem，
 * 此 hook 实际只在手持剑按右键格挡时触发。
 */
@Mixin(ItemInHandRenderer.class)
public class MixinItemInHandRenderer {

    @Inject(
        method = "submitArmWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V",
            ordinal = 7,
            shift = At.Shift.AFTER
        )
    )
    private void micx$oneSevenBlockOffset(
            AbstractClientPlayer player, float partialTicks, float equipProgress,
            InteractionHand hand, float swingProgress, ItemStack stack, float equipProg,
            PoseStack poseStack, SubmitNodeCollector collector, int light,
            CallbackInfo ci) {
        if (!SwordBlockModule.isEnabled() || !stack.is(ItemTags.SWORDS)) return;
        poseStack.translate(-0.5f, 0.2f, 0f);
    }
}
