package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.AntiAxeGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Minecraft;

/**
 * AntiAXE 的右键拦截点（Forge ASM 注入 Minecraft.rightClickMouse 的等价物）：
 * 在原版选择目标/发送交互包之前取消本次右键。
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftAntiAxe {

    @Inject(method = "startUseItem()V", at = @At("HEAD"), cancellable = true)
    private void micx$antiAxeGuard(CallbackInfo ci) {
        if (AntiAxeGuard.shouldBlockRightClick()) {
            ci.cancel();
        }
    }
}
