package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.SwordBlockModule;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让剑返回 BLOCK 使用动画（模拟 1.8.9 行为）。
 *
 * <p>26.2 剑返回 NONE，玩家按右键不进入使用状态。改为返回 BLOCK 后，
 * 玩家按右键会进入使用状态，ItemInHandRenderer 的 BLOCK 分支被触发，
 * 显示格挡姿态（配合 MixinItemInHandRenderer 追加 1.7 风格变换）。
 */
@Mixin(ItemStack.class)
public class MixinItemStack {

    @Inject(method = "getUseAnimation", at = @At("RETURN"), cancellable = true)
    private void micx$swordReturnsBlock(CallbackInfoReturnable<ItemUseAnimation> cir) {
        if (!SwordBlockModule.isEnabled()) return;
        // 只覆盖 NONE（避免影响食物/盾牌等已有动画）
        if (cir.getReturnValue() != ItemUseAnimation.NONE) return;
        ItemStack self = (ItemStack) (Object) this;
        if (self.getItem().builtInRegistryHolder().is(ItemTags.SWORDS)) {
            cir.setReturnValue(ItemUseAnimation.BLOCK);
        }
    }
}
